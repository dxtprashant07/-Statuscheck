import { useRef, useState } from "react";

interface FileDropzoneProps {
  label: string;
  accept?: string;
  uploading: boolean;
  uploadedFilename?: string | null;
  onFileSelected: (file: File) => void;
}

export default function FileDropzone({
  label,
  accept = ".docx,.pdf",
  uploading,
  uploadedFilename,
  onFileSelected
}: FileDropzoneProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [isActive, setIsActive] = useState(false);

  function handleFiles(files: FileList | null) {
    const file = files?.[0];
    if (file) onFileSelected(file);
  }

  return (
    <div
      className={`dropzone${isActive ? " is-active" : ""}`}
      onClick={() => inputRef.current?.click()}
      onDragOver={(e) => {
        e.preventDefault();
        setIsActive(true);
      }}
      onDragLeave={() => setIsActive(false)}
      onDrop={(e) => {
        e.preventDefault();
        setIsActive(false);
        handleFiles(e.dataTransfer.files);
      }}
      role="button"
      tabIndex={0}
    >
      <input
        ref={inputRef}
        type="file"
        accept={accept}
        onChange={(e) => handleFiles(e.target.files)}
      />
      <div className="dropzone__label">
        {uploading ? (
          <span className="row" style={{ justifyContent: "center" }}>
            <span className="spinner spinner--dark" /> Extracting…
          </span>
        ) : (
          <>
            <strong>{label}</strong>
            <div>Drop a .docx or .pdf here, or click to browse</div>
          </>
        )}
      </div>
      {uploadedFilename && !uploading && <div className="dropzone__filename">{uploadedFilename}</div>}
    </div>
  );
}
