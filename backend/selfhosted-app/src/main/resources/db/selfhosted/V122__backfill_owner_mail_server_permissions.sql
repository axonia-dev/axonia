-- An installation founded before mail_server:read and mail_server:write existed: its Owner was given every permission
-- the edition knew at its founding, and founding never runs again. A freshly founded installation needs nothing here.
INSERT INTO role_permission (role_id, permission)
SELECT r.id, p.permission
FROM role r
CROSS JOIN (VALUES ('mail_server:read'), ('mail_server:write')) AS p(permission)
WHERE r.system = true
  AND r.slug = 'owner'
  AND NOT EXISTS (
      SELECT 1 FROM role_permission rp
      WHERE rp.role_id = r.id AND rp.permission = p.permission
  );
