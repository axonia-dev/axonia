import { LoveCount } from "@/components/ui/LoveCount";
import { SectionCard } from "@/components/ui/SectionCard";
import { SectionRow } from "@/components/ui/SectionRow";
import { useUsers } from "@/features/identity/api/use-users";
import { useArtifactTypes } from "@/features/knowledge/api/use-artifact-types";
import { useArtifacts } from "@/features/knowledge/api/use-artifacts";
import type { Artifact } from "@/features/knowledge/domain/artifact";
import { TypeIcon } from "@/features/knowledge/ui/type-visual";

interface TypeVisual {
  icon?: string | null;
  accentColor?: string | null;
}

function LovedRow({
  slug,
  artifact,
  visual,
  contributor,
}: Readonly<{
  slug: string;
  artifact: Artifact;
  visual?: TypeVisual | undefined;
  contributor?: string | undefined;
}>) {
  return (
    <SectionRow
      to={`/communities/${slug}/library?focus=${artifact.id}`}
      leading={
        <TypeIcon
          typeId={artifact.typeId}
          label={artifact.type}
          icon={visual?.icon}
          accentColor={visual?.accentColor}
          size={20}
          className="size-10"
        />
      }
      title={artifact.title}
      {...(contributor
        ? { subtitle: <span className="truncate">by {contributor}</span> }
        : {})}
      meta={<LoveCount count={artifact.loveCount} />}
    />
  );
}

/**
 * The Library's most-appreciated items. Ranks CONTENT by love count - sanctioned
 * (mirrors the Library's "Most loved" sort). It must NEVER become a per-member
 * ranking: the product ranks no member against another.
 */
export function MostLovedFeed({ slug }: Readonly<{ slug: string }>) {
  const artifacts = useArtifacts(slug, {
    page: 0,
    size: 3,
    sort: "mostAppreciated",
  });
  const types = useArtifactTypes();
  const typeById = new Map<number, TypeVisual>(
    (types.data ?? []).map((type) => [type.id, type]),
  );
  const content = artifacts.data?.content ?? [];
  const userIds = [...new Set(content.map((a) => a.contributorUserId))];
  const users = useUsers(userIds);
  const loved = content.filter((artifact) => artifact.loveCount > 0);

  if (artifacts.isPending || loved.length === 0) {
    return null;
  }

  return (
    <SectionCard
      title="Most loved in the Library"
      icon="heart"
      accent="var(--primary)"
      action={{
        label: "Most loved",
        to: `/communities/${slug}/library?sort=mostAppreciated`,
      }}
    >
      <ul className="flex flex-col">
        {loved.map((artifact) => (
          <li key={artifact.id}>
            <LovedRow
              slug={slug}
              artifact={artifact}
              {...(typeById.has(artifact.typeId)
                ? { visual: typeById.get(artifact.typeId) }
                : {})}
              {...(users.data?.[artifact.contributorUserId]
                ? {
                    contributor:
                      users.data[artifact.contributorUserId]?.displayName,
                  }
                : {})}
            />
          </li>
        ))}
      </ul>
    </SectionCard>
  );
}
