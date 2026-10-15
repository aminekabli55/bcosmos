CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS repository (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    canonical_local_path VARCHAR(1000),
    source_type VARCHAR(20) NOT NULL,
    primary_language VARCHAR(40),
    remote_url VARCHAR(1000),
    default_branch VARCHAR(200),
    license_spdx VARCHAR(80),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS analysis_run (
    id BIGSERIAL PRIMARY KEY,
    repository_id BIGINT NOT NULL REFERENCES repository(id) ON DELETE CASCADE,
    commit_hash VARCHAR(64),
    status VARCHAR(20) NOT NULL,
    stage VARCHAR(30),
    parser_version VARCHAR(120),
    analyzer_version VARCHAR(120),
    ruleset_hash VARCHAR(64),
    schema_version INT NOT NULL,
    files_discovered INT NOT NULL DEFAULT 0,
    files_parsed INT NOT NULL DEFAULT 0,
    files_failed INT NOT NULL DEFAULT 0,
    error_code VARCHAR(60),
    error_summary VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS file_record (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES analysis_run(id) ON DELETE CASCADE,
    relative_path VARCHAR(1000) NOT NULL,
    language VARCHAR(40) NOT NULL,
    content_hash VARCHAR(64),
    line_count INT NOT NULL DEFAULT 0,
    parse_status VARCHAR(20) NOT NULL,
    parse_error VARCHAR(1000)
);

CREATE TABLE IF NOT EXISTS finding (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES analysis_run(id) ON DELETE CASCADE,
    file_id BIGINT NOT NULL REFERENCES file_record(id) ON DELETE CASCADE,
    tool VARCHAR(40) NOT NULL,
    tool_version VARCHAR(120) NOT NULL,
    rule_id VARCHAR(200) NOT NULL,
    original_severity VARCHAR(40) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    severity_rank INT NOT NULL DEFAULT 0,
    message VARCHAR(1000) NOT NULL,
    start_line INT NOT NULL,
    start_column INT NOT NULL,
    end_line INT NOT NULL,
    end_column INT NOT NULL,
    symbol_qualified_name VARCHAR(1000),
    snippet VARCHAR(600),
    normalized_snippet VARCHAR(600),
    fingerprint VARCHAR(64) NOT NULL,
    triage_status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS commit_record (
    id BIGSERIAL PRIMARY KEY,
    repository_id BIGINT NOT NULL REFERENCES repository(id) ON DELETE CASCADE,
    hash VARCHAR(64) NOT NULL,
    parent_hashes TEXT[],
    committed_at TIMESTAMPTZ NOT NULL,
    author_hash VARCHAR(64),
    changed_java_files INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_commit_record_repository_hash UNIQUE (repository_id, hash)
);

CREATE TABLE IF NOT EXISTS worker_file_result (
    id BIGSERIAL PRIMARY KEY,
    repository_id BIGINT NOT NULL REFERENCES repository(id) ON DELETE CASCADE,
    commit_hash VARCHAR(64) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    side VARCHAR(16) NOT NULL,
    analyzer_version VARCHAR(120) NOT NULL,
    ruleset_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    finding_count INT NOT NULL DEFAULT 0,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_worker_file_result_key UNIQUE (repository_id, commit_hash, file_path, side, analyzer_version, ruleset_hash)
);
