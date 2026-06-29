import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api/client";
import type { Project } from "../types";

export default function ProjectsPage() {
  const [projects, setProjects] = useState<Project[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [creating, setCreating] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);

  useEffect(() => {
    api
      .listProjects()
      .then(setProjects)
      .catch((e) => setError(e.message));
  }, []);

  async function handleCreate() {
    if (!name.trim()) return;
    setCreating(true);
    setError(null);
    try {
      const project = await api.createProject(name.trim(), description.trim());
      setProjects((prev) => [project, ...(prev ?? [])]);
      setName("");
      setDescription("");
      setShowForm(false);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setCreating(false);
    }
  }

  async function handleDelete(id: number, projectName: string) {
    if (!window.confirm(`Delete "${projectName}"? This removes its documents, items and comparison — this cannot be undone.`)) {
      return;
    }
    setDeletingId(id);
    setError(null);
    try {
      await api.deleteProject(id);
      setProjects((prev) => (prev ?? []).filter((p) => p.id !== id));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setDeletingId(null);
    }
  }

  return (
    <div className="stack">
      <div className="toolbar">
        <div>
          <h1>Projects</h1>
          <p className="muted">Compare a proposal against its status report for any project.</p>
        </div>
        <button className="btn btn-primary" onClick={() => setShowForm((v) => !v)}>
          New project
        </button>
      </div>

      {error && <div className="error-banner">{error}</div>}

      {showForm && (
        <div className="card card-pad">
          <div className="field">
            <label htmlFor="name">Project name</label>
            <input id="name" value={name} onChange={(e) => setName(e.target.value)} placeholder="NxtGenEPRAVAH" />
          </div>
          <div className="field">
            <label htmlFor="description">Description (optional)</label>
            <input
              id="description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Payroll module rollout, Q3"
            />
          </div>
          <button className="btn btn-primary" onClick={handleCreate} disabled={creating || !name.trim()}>
            {creating ? <span className="spinner" /> : null}
            Create project
          </button>
        </div>
      )}

      {projects === null ? (
        <p className="muted">Loading…</p>
      ) : projects.length === 0 ? (
        <div className="card empty-state">
          <p>No projects yet. Create one to upload a proposal and status report.</p>
        </div>
      ) : (
        <div className="project-grid">
          {projects.map((project) => (
            <Link to={`/projects/${project.id}`} key={project.id} className="card project-card">
              <button
                className="project-card__delete"
                title="Delete project"
                aria-label={`Delete ${project.name}`}
                disabled={deletingId === project.id}
                onClick={(e) => {
                  e.preventDefault();
                  e.stopPropagation();
                  handleDelete(project.id, project.name);
                }}
              >
                {deletingId === project.id ? "…" : "🗑"}
              </button>
              <h3>{project.name}</h3>
              <p>{project.description || "No description"}</p>
              <div className="project-card__meta">
                created {new Date(project.createdAt).toLocaleDateString()}
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
