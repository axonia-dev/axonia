# Storage for images

Members' avatars, community heroes and the other images people upload live in one of three stores, chosen by `APP_MEDIA_TYPE` (in the Helm chart, `media.type`).
Switching store later is a data migration, not a setting.

## The store stays private

No store needs public access: the backend serves every image itself.
Pages load images from their own origin at `/public/media/`; a mail loads them from `<AXONIA_PUBLIC_BASE_URL>/public/media/`, so that setting must be the address mail clients reach.
There is no media URL setting.

## On a volume (`local`, the default)

- The image writes to `/var/lib/axonia/media`; the compose file and the chart mount a volume there.
  A path that is not a mounted volume loses every image at the next restart.
- A volume that mounts on one node at a time (`ReadWriteOnce`) allows one backend instance, and an upgrade replaces the pod rather than rolling it.
  More than one instance needs `ReadWriteMany` in `media.local.accessModes` or a bucket; the chart refuses the rest.
- Some `ReadWriteMany` drivers (NFS among them) ignore the pod's group, so the backend, user and group 10001, cannot write; give the share to 10001, or use a bucket.

| Helm value | Default | Value |
| --- | --- | --- |
| `media.local.size` | `10Gi` | The size of the claim the chart creates |
| `media.local.storageClassName` | the cluster's default | The storage class of that claim |
| `media.local.accessModes` | `ReadWriteOnce` | The claim's access modes |
| `media.local.existingClaim` | | A claim to mount instead of creating one, for example a restored one |

An uninstall keeps the claim the chart created, as it keeps the database.

## Azure Blob Storage (`azure-blob`)

For AKS, where the backend reads and writes images with its own identity and no storage key exists.

1. Create a storage account, or use an existing one, with "Allow Blob anonymous access" off and "Allow storage account key access" off.
   A private endpoint in the cluster's network is fine: only the backend talks to it.
2. Create a container, for example `axonia-media`.
3. Create a user-assigned managed identity for the backend, or reuse the one the backend already has for other resources.
4. On the container, Access control (IAM), add the role assignment "Storage Blob Data Contributor" for that identity.
   Scope it to the container, not the account.
5. On the identity, Federated credentials, add one for "Kubernetes accessing Azure resources": the cluster's OIDC issuer URL, the namespace, and the backend's service account name (`<release>-backend`).
6. In the Helm values, set `backend.serviceAccount.annotations` to `azure.workload.identity/client-id: <identity client id>` and `backend.podLabels` to `azure.workload.identity/use: "true"`.
7. Backend settings:

| Variable | Helm value | Value |
| --- | --- | --- |
| `APP_MEDIA_TYPE` | `media.type` | `azure-blob` |
| `APP_MEDIA_AZURE_BLOB_ENDPOINT` | `media.azureBlob.endpoint` | `https://<account>.blob.core.windows.net` (must be `https`; the backend refuses to start otherwise) |
| `APP_MEDIA_AZURE_BLOB_CONTAINER` | `media.azureBlob.container` | the container's name (an unresolved placeholder here refuses the start, as for the endpoint) |
| `AZURE_TOKEN_CREDENTIALS` | set by the chart | `prod` |

The chart sets `AZURE_TOKEN_CREDENTIALS=prod`, so only the platform's identity or a service principal named in the environment is used, never a developer tool's sign-in.
`deploy/helm/examples/entra-aks-azure-blob.yaml` holds these values.

Keep no blob versions on the container, and check the account's soft delete for blobs: both keep a deleted picture recoverable, so a member's erased avatar outlives the erasure for the retention window.
New accounts created in the Azure portal have blob soft delete on by default; either turn it off or keep its retention short enough for the privacy notice to state it.

8. Check, before the installation is handed over and once per major release: upload an avatar, see it render, and see the blob in the container; delete the avatar and see the blob go.
   The metric `axonia.integration.calls` with `integration="azure-blob"` shows no `outcome="failure"`.
   A 403 from storage means step 4 or 5 is missing; a credential error at start means step 6 is.
   With a private endpoint, a name resolution or connection error at the first upload means the cluster's DNS does not resolve `<account>.blob.core.windows.net` to the private address.

The same store can be tried from a developer machine before AKS exists: `az login` as a user holding the role from step 4, then start the backend with the settings from step 7 except `AZURE_TOKEN_CREDENTIALS`, which would refuse the Azure CLI's sign-in.

## An S3-compatible bucket (`s3`)

For AWS S3, or a store that speaks its API (MinIO, Ceph, SeaweedFS, Cloudflare R2).
The bucket stays private: only the backend talks to it.

1. Create the bucket, with public access blocked.
2. Give the backend's identity these permissions, and nothing wider:
   `s3:GetObject`, `s3:PutObject` and `s3:DeleteObject` on `arn:aws:s3:::<bucket>/*`, and `s3:ListBucket` on `arn:aws:s3:::<bucket>`.
   Without `s3:ListBucket`, AWS answers a request for a deleted image with 403 instead of 404, so the backend answers 500 and counts a store failure where it should answer 404.
3. Backend settings:

| Variable | Helm value | Value |
| --- | --- | --- |
| `APP_MEDIA_TYPE` | `media.type` | `s3` |
| `APP_MEDIA_S3_BUCKET` | `media.s3.bucket` | the bucket's name |
| `APP_MEDIA_S3_REGION` | `media.s3.region` | the bucket's region, for example `eu-central-1`; left out, it is `auto`, which is right for Cloudflare R2 only |
| `APP_MEDIA_S3_ENDPOINT` | `media.s3.endpoint` | the store's address, for example `https://minio.example.com`; required while the region is `auto`, and left out for AWS S3 itself |
| `APP_MEDIA_S3_PATH_STYLE` | `media.s3.pathStyle` | `true` for most stores run in-house (MinIO, Ceph, SeaweedFS), which do not serve a bucket as its own host name; left out, it is `false`, which AWS S3 and R2 expect |
| `APP_MEDIA_S3_ACCESS_KEY` and `APP_MEDIA_S3_SECRET_KEY` | `media.s3.existingSecret` | both left out, to use the platform's identity; or both set, to use a static key pair |

With both keys left out, the backend uses the AWS default credential chain: EKS Pod Identity, a web identity token (IRSA), or an instance profile.
With IRSA, set `backend.serviceAccount.annotations` to `eks.amazonaws.com/role-arn: <the role's ARN>`, as `deploy/helm/examples/eks-s3-redis.yaml` does.
One key without the other refuses the start, naming both settings, and so does a key set to an empty value or left as an unresolved placeholder.
The Helm chart renders the keys only when `media.s3.existingSecret` names a Secret holding both, under the keys `APP_MEDIA_S3_ACCESS_KEY` and `APP_MEDIA_S3_SECRET_KEY`; with Docker Compose, leave both variables out entirely rather than empty.
An unresolved placeholder in the endpoint, the bucket or the region refuses the start too.
Keep no object versions in the bucket: with bucket versioning on, a deleted picture stays recoverable, so a member's erased avatar outlives the erasure; if versioning must stay on, a lifecycle rule that expires noncurrent versions sets a retention the privacy notice can state.

4. Check, as for Azure Blob Storage: upload an avatar, see it render, and see the object in the bucket; delete the avatar and see the object go.
   The metric `axonia.integration.calls` with `integration="s3"` counts every call to an S3-compatible store, whatever the store is, and shows no `outcome="failure"`.
   A 403 at the first upload means step 2 is missing; a credential error means the platform gives the pod no identity.
