CREATE TABLE category (
    id          BIGSERIAL PRIMARY KEY,
    slug        VARCHAR(50)  NOT NULL UNIQUE,
    name_ko     VARCHAR(50)  NOT NULL,
    sort_order  INT          NOT NULL
);

CREATE TABLE repo (
    id              BIGSERIAL PRIMARY KEY,
    github_id       BIGINT       NOT NULL UNIQUE,
    full_name       VARCHAR(200) NOT NULL UNIQUE,
    description     TEXT,
    language        VARCHAR(50),
    license         VARCHAR(100),
    html_url        VARCHAR(300) NOT NULL,
    last_pushed_at  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE star_snapshot (
    id             BIGSERIAL PRIMARY KEY,
    repo_id        BIGINT NOT NULL REFERENCES repo(id),
    snapshot_date  DATE   NOT NULL,
    stars          INT    NOT NULL,
    UNIQUE (repo_id, snapshot_date)
);

CREATE TABLE repo_analysis (
    id           BIGSERIAL PRIMARY KEY,
    repo_id      BIGINT       NOT NULL UNIQUE REFERENCES repo(id),
    useful       BOOLEAN      NOT NULL,
    category_id  BIGINT       REFERENCES category(id),
    summary_ko   TEXT,
    install_cmd  VARCHAR(300),
    confidence   NUMERIC(3,2) NOT NULL,
    status       VARCHAR(20)  NOT NULL,      -- PENDING_REVIEW, PUBLISHED, REJECTED
    raw          JSONB        NOT NULL,      -- Gemini 원본 응답
    analyzed_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_repo_analysis_status ON repo_analysis(status);

CREATE TABLE daily_ranking (
    id            BIGSERIAL PRIMARY KEY,
    ranking_date  DATE   NOT NULL,
    category_id   BIGINT NOT NULL REFERENCES category(id),
    rank_no       INT    NOT NULL,
    repo_id       BIGINT NOT NULL REFERENCES repo(id),
    star_gain     INT    NOT NULL,
    UNIQUE (ranking_date, category_id, rank_no)
);

CREATE TABLE app_user (
    id          BIGSERIAL PRIMARY KEY,
    github_id   BIGINT       NOT NULL UNIQUE,
    login       VARCHAR(100) NOT NULL,
    avatar_url  VARCHAR(300),
    role        VARCHAR(20)  NOT NULL,       -- USER, ADMIN
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE bookmark (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES app_user(id),
    repo_id     BIGINT      NOT NULL REFERENCES repo(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, repo_id)
);

CREATE TABLE batch_run (
    id           BIGSERIAL PRIMARY KEY,
    run_date     DATE        NOT NULL UNIQUE,
    status       VARCHAR(20) NOT NULL,       -- RUNNING, SUCCESS, FAILED
    step         VARCHAR(20) NOT NULL,       -- DISCOVER, COLLECT, ANALYZE, RANK, DONE
    started_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at  TIMESTAMPTZ,
    error        TEXT
);

INSERT INTO category (slug, name_ko, sort_order) VALUES
    ('ai-agent',     'AI·에이전트',     1),
    ('dev-tools',    '개발 도구·CLI',   2),
    ('frontend',     '프론트엔드',      3),
    ('backend',      '백엔드·API',      4),
    ('data',         '데이터·DB',       5),
    ('devops',       '데브옵스·인프라', 6),
    ('security',     '보안',            7),
    ('productivity', '생산성·자동화',   8);
