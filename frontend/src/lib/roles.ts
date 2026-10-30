import { UserDto, RoleCode } from "../types/auth";

const ROLE_HIERARCHY: Record<RoleCode, number> = {
  SUPER_ADMIN: 50,
  ORGANIZATION_ADMIN: 40,
  BRANCH_ADMIN: 30,
  STAFF: 20,
  CUSTOMER: 10,
};

/** Check if user has a specific role. */
export function hasRole(user: UserDto | null, role: RoleCode): boolean {
  if (!user?.roles) return false;
  return user.roles.includes(role);
}

/** Check if user has any of the specified roles. */
export function hasAnyRole(user: UserDto | null, roles: RoleCode[]): boolean {
  if (!user?.roles) return false;
  return roles.some((r) => user.roles.includes(r));
}

/** Get the user's highest-privilege role based on the hierarchy. */
export function getHighestRole(user: UserDto | null): RoleCode | null {
  if (!user?.roles || user.roles.length === 0) return null;
  let highest: RoleCode | null = null;
  let highestLevel = -1;
  for (const role of user.roles) {
    const level = ROLE_HIERARCHY[role as RoleCode] ?? -1;
    if (level > highestLevel) {
      highestLevel = level;
      highest = role as RoleCode;
    }
  }
  return highest;
}

/** Check if user is an admin of any kind (ORG, BRANCH, or SUPER). */
export function isAdmin(user: UserDto | null): boolean {
  return hasAnyRole(user, ["SUPER_ADMIN", "ORGANIZATION_ADMIN", "BRANCH_ADMIN"]);
}

/** Check if user can manage queues (staff or above). */
export function canManageQueues(user: UserDto | null): boolean {
  return hasAnyRole(user, ["SUPER_ADMIN", "ORGANIZATION_ADMIN", "BRANCH_ADMIN", "STAFF"]);
}

/** Get a human-readable label for a role. */
export function getRoleLabel(role: RoleCode): string {
  const labels: Record<RoleCode, string> = {
    SUPER_ADMIN: "Super Admin",
    ORGANIZATION_ADMIN: "Organization Admin",
    BRANCH_ADMIN: "Branch Admin",
    STAFF: "Staff",
    CUSTOMER: "Customer",
  };
  return labels[role] ?? role;
}
