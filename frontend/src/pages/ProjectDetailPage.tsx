import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api, authHeaders } from "../api/client";
import type { ComparisonResult, Project, ProjectItemsResponse } from "../types";
import FileDropzone from "../components/FileDropzone";
import ResultsTable from "../components/ResultsTable";

export default function ProjectDetailPage() {
  const { projectId } = useParams();
  const id = Number(projectId);

  const [project, setProject] = useState<Project | null>(null);
  const [items, setItems] = useState<ProjectItemsResponse | null>(null);
  const [results, setResults] = useState<ComparisonResult[]>([]);
  const [error, setError] = useState<string | null>(null);

  const [uploadingProposal, setUploadingProposal] = useState(false);
  const [uploadingStatus, setUploadingStatus] = useState(false);
  const [proposalFilename, setProposalFilename] = useState<string | null>(null);
  const [statusFilename, setStatusFilename] = useState<string | null>(null);
  const [running, setRunning] = useState(false);
  const [downloading, setDownloading] = useState<"word" | "ppt" | null>(null);

  const refresh = useCallback(async () => {
    const [p, i, c] = await Promise.all([api.getProject(id), api.getItems(id), api.getComparison(id)]);
    setProject(p);
    setItems(i);
    setResults(c);
  }, [id]);

  useEffect(() => {
    refresh().catch((e) => setError(e.message));
  }, [refresh]);

  async function handleProposalUpload(file: File) {
    setUploadingProposal(true);
    setError(null);
    try {
      const doc = await api.uploadProposal(id, file);
      setProposalFilename(doc.originalFilename);
      await refresh();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setUploadingProposal(false);
    }
  }

  async function handleStatusUpload(file: File) {
    setUploadingStatus(true);
    setError(null);
    try {
      const doc = await api.uploadStatusReport(id, file);
      setStatusFilename(doc.originalFilename);
      await refresh();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setUploadingStatus(false);
    }
  }

  async function handleRunComparison() {
    setRunning(true);
    setError(null);
    try {
      const comparisonResults = await api.runComparison(id);
      setResults(comparisonResults);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setRunning(false);
    }
  }

  async function handleDownload(kind: "word" | "ppt") {
    setDownloading(kind);
    setError(null);
    try {
      const response = await fetch(api.reportUrl(id, kind), { headers: authHeaders() });
      if (!response.ok) {
        let message = `Download failed with status ${response.status}`;
        try {
          const body = await response.json();
          if (body?.error) message = body.error;
        } catch {
          // non-JSON error body - keep the generic message
        }
        throw new Error(message);
      }
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `${project?.name ?? "status-report"}-status-report.${kind === "word" ? "docx" : "pptx"}`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setDownloading(null);
    }
  }

  const proposalCount = items?.proposalItems.length ?? 0;
  const statusCount = items?.statusItems.length ?? 0;
  const canRun = proposalCount > 0 && !running;

  return (
    <div className="stack">
      <div>
        <Link to="/" className="muted">
          ← All projects
        </Link>
      </div>

      <div className="toolbar">
        <div>
          <h1>{project?.name ?? "…"}</h1>
          {project?.description && <p className="muted">{project.description}</p>}
        </div>
        <div className="row">
          <button
            className="btn"
            disabled={results.length === 0 || downloading !== null}
            onClick={() => handleDownload("word")}
          >
            {downloading === "word" ? <span className="spinner" /> : null}
            Download Word
          </button>
          <button
            className="btn"
            disabled={results.length === 0 || downloading !== null}
            onClick={() => handleDownload("ppt")}
          >
            {downloading === "ppt" ? <span className="spinner" /> : null}
            Download PPT
          </button>
        </div>
      </div>

      {error && <div className="error-banner">{error}</div>}

      <div className="intake-grid">
        <div className="card card-pad">
          <FileDropzone
            label="Proposal"
            uploading={uploadingProposal}
            uploadedFilename={proposalFilename}
            onFileSelected={handleProposalUpload}
          />
          <div className="intake-card__count">{proposalCount} item{proposalCount === 1 ? "" : "s"} extracted</div>
        </div>
        <div className="card card-pad">
          <FileDropzone
            label="Status report"
            uploading={uploadingStatus}
            uploadedFilename={statusFilename}
            onFileSelected={handleStatusUpload}
          />
          <div className="intake-card__count">{statusCount} item{statusCount === 1 ? "" : "s"} extracted</div>
        </div>
      </div>

      <div className="section-heading">
        <h2 style={{ fontSize: 17 }}>Comparison</h2>
        <button className="btn btn-primary" onClick={handleRunComparison} disabled={!canRun}>
          {running ? <span className="spinner" /> : null}
          {results.length > 0 ? "Re-run comparison" : "Run comparison"}
        </button>
      </div>

      <div className="card">
        <ResultsTable
          results={results}
          projectId={id}
          onResultUpdated={(updated) =>
            setResults((prev) => prev.map((r) => (r.id === updated.id ? updated : r)))
          }
        />
      </div>
    </div>
  );
}
