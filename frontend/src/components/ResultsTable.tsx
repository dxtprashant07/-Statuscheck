import { useMemo, useState } from "react";
import { api } from "../api/client";
import type { ComparisonResult, ItemStatus } from "../types";
import StatusBadge, { statusRowClass, LABELS } from "./StatusBadge";

const STATUS_OPTIONS = Object.keys(LABELS) as ItemStatus[];

interface Props {
  results: ComparisonResult[];
  projectId: number;
  onResultUpdated: (updated: ComparisonResult) => void;
}

export default function ResultsTable({ results, projectId, onResultUpdated }: Props) {
  const [query, setQuery] = useState("");
  const [editingId, setEditingId] = useState<number | null>(null);
  const [draftStatus, setDraftStatus] = useState<ItemStatus>("COMPLETED");
  const [draftEvidence, setDraftEvidence] = useState("");
  const [saving, setSaving] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return results;
    return results.filter((result) =>
      [
        result.proposalItemTitle,
        result.proposalItemDescription,
        result.statusItemTitle,
        result.evidence,
        LABELS[result.status],
        result.matchedBy
      ]
        .filter((field): field is string => Boolean(field))
        .some((field) => field.toLowerCase().includes(q))
    );
  }, [results, query]);

  function startEdit(result: ComparisonResult) {
    setEditingId(result.id);
    setDraftStatus(result.status);
    setDraftEvidence(result.evidence ?? "");
    setEditError(null);
  }

  function cancelEdit() {
    setEditingId(null);
    setEditError(null);
  }

  async function saveEdit(result: ComparisonResult) {
    setSaving(true);
    setEditError(null);
    try {
      const evidence = draftEvidence.trim() ? draftEvidence.trim() : null;
      const updated = await api.updateComparison(projectId, result.id, { status: draftStatus, evidence });
      onResultUpdated(updated);
      setEditingId(null);
    } catch (e) {
      setEditError((e as Error).message);
    } finally {
      setSaving(false);
    }
  }

  if (results.length === 0) {
    return (
      <div className="empty-state">
        <p>No comparison yet. Upload both documents, then run the comparison.</p>
      </div>
    );
  }

  return (
    <div className="results">
      <div className="results__search">
        <span className="results__search-icon" aria-hidden="true">
          ⌕
        </span>
        <input
          type="search"
          className="results__search-input"
          placeholder="Search items, status or evidence…"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          aria-label="Search comparison results"
        />
        {query.trim() && (
          <span className="results__search-count">
            {filtered.length} of {results.length}
          </span>
        )}
      </div>

      {filtered.length === 0 ? (
        <div className="empty-state">
          <p>No results match “{query.trim()}”.</p>
        </div>
      ) : (
        <table className="results-table">
          <thead>
            <tr>
              <th style={{ width: "28%" }}>Planned item</th>
              <th style={{ width: "14%" }}>Status</th>
              <th>Evidence from status report</th>
              <th style={{ width: "9%" }}>Matched by</th>
              <th style={{ width: "1%" }} aria-label="Actions" />
            </tr>
          </thead>
          <tbody>
            {filtered.map((result) => {
              const editing = editingId === result.id;
              return (
                <tr key={result.id} className={statusRowClass(result.status)}>
                  <td>
                    <div>{result.proposalItemTitle}</div>
                    {result.proposalItemDescription && (
                      <div className="muted">{result.proposalItemDescription}</div>
                    )}
                  </td>
                  <td>
                    {editing ? (
                      <select
                        className="results-edit__select"
                        value={draftStatus}
                        onChange={(event) => setDraftStatus(event.target.value as ItemStatus)}
                        aria-label="Status"
                      >
                        {STATUS_OPTIONS.map((option) => (
                          <option key={option} value={option}>
                            {LABELS[option]}
                          </option>
                        ))}
                      </select>
                    ) : (
                      <StatusBadge status={result.status} />
                    )}
                  </td>
                  <td className="results-table__evidence">
                    {editing ? (
                      <>
                        <textarea
                          className="results-edit__evidence"
                          rows={3}
                          value={draftEvidence}
                          placeholder="Evidence from the status report…"
                          onChange={(event) => setDraftEvidence(event.target.value)}
                          aria-label="Evidence"
                        />
                        {editError && <div className="results-edit__error">{editError}</div>}
                      </>
                    ) : result.evidence ? (
                      result.evidence
                    ) : (
                      <span className="muted">Not mentioned in the status report</span>
                    )}
                  </td>
                  <td>
                    <span className="matched-by">{editing ? "MANUAL" : result.matchedBy}</span>
                  </td>
                  <td className="results-table__actions">
                    {editing ? (
                      <div className="results-edit__actions">
                        <button
                          className="btn btn-sm btn-primary"
                          disabled={saving}
                          onClick={() => saveEdit(result)}
                        >
                          {saving ? <span className="spinner" /> : null}
                          Save
                        </button>
                        <button className="btn btn-sm" disabled={saving} onClick={cancelEdit}>
                          Cancel
                        </button>
                      </div>
                    ) : (
                      <button
                        className="btn btn-sm"
                        disabled={editingId !== null}
                        onClick={() => startEdit(result)}
                      >
                        Edit
                      </button>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </div>
  );
}
