export type ItemStatus = "COMPLETED" | "IN_PROGRESS" | "PENDING" | "AT_RISK" | "NOT_STARTED";

export type ExtractionMethod = "RULE" | "LLM" | "MANUAL";

export interface Project {
  id: number;
  name: string;
  description: string | null;
  createdAt: string;
}

export interface DocumentSummary {
  id: number;
  originalFilename: string;
  uploadedAt: string;
}

export interface ProjectItem {
  id: number;
  title: string;
  description: string | null;
  plannedDate: string | null;
  module: string | null;
  extractionMethod: ExtractionMethod;
}

export interface ProjectItemsResponse {
  proposalItems: ProjectItem[];
  statusItems: ProjectItem[];
}

export interface ComparisonResult {
  id: number;
  proposalItemTitle: string;
  proposalItemDescription: string | null;
  statusItemTitle: string | null;
  status: ItemStatus;
  evidence: string | null;
  matchedBy: ExtractionMethod;
}
