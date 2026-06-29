import type { ItemStatus } from "../types";

export const LABELS: Record<ItemStatus, string> = {
  COMPLETED: "Completed",
  IN_PROGRESS: "In progress",
  PENDING: "Pending",
  AT_RISK: "At risk",
  NOT_STARTED: "Not started"
};

const CLASS_SUFFIX: Record<ItemStatus, string> = {
  COMPLETED: "completed",
  IN_PROGRESS: "in-progress",
  PENDING: "pending",
  AT_RISK: "at-risk",
  NOT_STARTED: "not-started"
};

export default function StatusBadge({ status }: { status: ItemStatus }) {
  return <span className={`status-badge status-badge--${CLASS_SUFFIX[status]}`}>{LABELS[status]}</span>;
}

export function statusRowClass(status: ItemStatus) {
  return `results-row results-row--${CLASS_SUFFIX[status]}`;
}
