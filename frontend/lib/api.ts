// 백엔드 응답 타입과 호출 함수.
// 서버 컴포넌트는 BACKEND_URL로 직접, 브라우저는 상대 경로(/api/...)로 호출한다 (next.config.ts rewrite).
//
// [CS] 이 파일은 프론트와 백엔드 사이의 "계약서"다. 백엔드 record(DTO)와 같은 모양의 TypeScript 타입을 두면,
// 필드 이름을 잘못 쓰면 빌드(타입 검사) 단계에서 바로 오류가 난다. 호출 코드를 한곳에 모으면
// 주소·헤더·오류 처리 규칙을 한 번만 고치면 된다.

// [문법] type 별칭: 객체 모양을 이름으로 정의한다. string | null 은 "문자열 또는 null"(유니언 타입)
export type Category = { slug: string; nameKo: string };

export type RankingItem = {
  rank: number;
  repoId: number;
  fullName: string;
  summaryKo: string | null;
  installCmd: string | null;
  starGain: number;
  language: string | null;
  htmlUrl: string;
};

/** date가 null이면 아직 계산된 순위가 없다 */
export type CategoryRanking = {
  slug: string;
  nameKo: string;
  date: string | null;
  items: RankingItem[];
};

export type RepoDetail = {
  id: number;
  fullName: string;
  description: string | null;
  language: string | null;
  license: string | null;
  htmlUrl: string;
  stars: number | null;
  categorySlug: string | null;
  categoryNameKo: string | null;
  summaryKo: string | null;
  installCmd: string | null;
  lastPushedAt: string | null;
};

export type StarPoint = { date: string; stars: number };

// [문법] 문자열 리터럴 유니언: role에는 "USER" 또는 "ADMIN"만 들어갈 수 있다 (자바 enum과 같은 역할)
export type Me = { login: string; avatarUrl: string | null; role: "USER" | "ADMIN" };

export type Bookmark = {
  repoId: number;
  fullName: string;
  summaryKo: string | null;
  language: string | null;
  htmlUrl: string;
  createdAt: string;
};

export type ReviewItem = {
  id: number;
  repoId: number;
  fullName: string;
  description: string | null;
  htmlUrl: string;
  useful: boolean;
  categorySlug: string | null;
  summaryKo: string | null;
  installCmd: string | null;
  confidence: number;
  raw: string;
};

// [문법] ?: 는 선택 속성(optional). 없어도 된다
export type ReviewRequest = {
  decision: "APPROVE" | "REJECT";
  categorySlug?: string;
  summaryKo?: string;
};

/** HTTP 상태 코드를 함께 담은 오류. 호출한 쪽이 401(로그인 필요) 같은 경우를 구분할 수 있다 */
export class ApiError extends Error {
  // [문법] 생성자 매개변수에 public을 붙이면 같은 이름의 필드 선언 + 대입이 한 번에 된다
  constructor(public status: number, message: string) {
    super(message);
  }
}

const HOUR = 3600;
// Render 무료 인스턴스가 잠에서 깨는 데 1분 가까이 걸릴 수 있다
const SERVER_TIMEOUT_MS = 60_000;

/**
 * [CS] 같은 코드가 두 곳에서 실행된다: 서버(Next.js 서버 컴포넌트, Node.js)와 브라우저.
 * typeof window === "undefined" 이면 서버다(브라우저 전역 객체 window가 없음).
 * 서버에서는 rewrite를 거치지 않고 백엔드에 직접 요청하고, 브라우저에서는 상대 경로로 요청해 rewrite를 탄다.
 */
function backendUrl(path: string) {
  return typeof window === "undefined" ? `${process.env.BACKEND_URL ?? "http://localhost:8080"}${path}` : path;
}

/**
 * 모든 API 호출의 공통 처리. 404는 null, 그 밖의 실패는 ApiError.
 * [문법] 제네릭 <T>: 호출하는 쪽이 request<Category[]>(...)처럼 응답 타입을 정한다.
 * async 함수는 항상 Promise를 돌려주고, await는 Promise가 끝날 때까지 기다렸다가 결과값을 꺼낸다(비동기를 동기처럼 읽히게).
 * [문법] RequestInit & {...}: 교차 타입. fetch 기본 옵션에 Next.js 전용 next.revalidate 옵션을 더한다
 */
async function request<T>(path: string, init?: RequestInit & { next?: { revalidate?: number } }): Promise<T | null> {
  const isServer = typeof window === "undefined";
  const res = await fetch(backendUrl(path), {
    ...init, // [문법] 스프레드: init 객체의 속성을 모두 펼쳐 복사한다. 뒤에 쓴 속성이 앞의 값을 덮어쓴다
    credentials: "same-origin", // 같은 출처 요청에는 쿠키(세션)를 함께 보낸다
    // 서버에서만 시간 제한을 건다. 백엔드가 안 깨어나면 60초 뒤 포기하고 error.tsx 화면을 보여준다
    signal: isServer ? AbortSignal.timeout(SERVER_TIMEOUT_MS) : undefined,
    headers: { Accept: "application/json", ...csrfHeader(init?.method), ...init?.headers },
  });
  if (res.status === 404) return null;
  if (!res.ok) throw new ApiError(res.status, `${init?.method ?? "GET"} ${path} 실패 (${res.status})`);
  // 204 No Content처럼 본문이 없는 응답은 JSON으로 해석하지 않는다
  if (res.status === 204 || res.headers.get("content-length") === "0") return null;
  const text = await res.text();
  return text ? (JSON.parse(text) as T) : null; // as T: "이 값은 T 타입"이라고 컴파일러에 알린다(실행 중 검사는 아님)
}

/**
 * 변경 요청에는 Spring Security가 내려준 XSRF-TOKEN 쿠키 값을 헤더로 붙인다.
 * [CS] CSRF 방어(Double Submit Cookie): 우리 페이지의 JS만 우리 쿠키를 읽을 수 있으므로,
 * 쿠키 값을 헤더로 되돌려 보내면 "진짜 우리 화면에서 보낸 요청"임이 증명된다. GET은 상태를 바꾸지 않아 붙이지 않는다.
 * [문법] Record<string, string>: 키도 값도 문자열인 객체 타입. ?. (옵셔널 체이닝): 앞이 undefined면 멈추고 undefined를 돌려준다
 */
function csrfHeader(method?: string): Record<string, string> {
  if (typeof document === "undefined" || !method || method === "GET") return {};
  const token = document.cookie
    .split("; ")
    .find((c) => c.startsWith("XSRF-TOKEN="))
    ?.slice("XSRF-TOKEN=".length);
  return token ? { "X-XSRF-TOKEN": decodeURIComponent(token) } : {};
}

// encodeURIComponent: ?, &, / 같은 특수문자를 URL에서 안전한 %XX 형태로 바꾼다(쿼리 문자열 깨짐·주입 방지)
const dateQuery = (date?: string) => (date ? `?date=${encodeURIComponent(date)}` : "");

// ── 공개 (서버 컴포넌트에서 1시간 캐시) ──
// [CS] next.revalidate: Next.js 서버가 fetch 결과를 1시간 동안 저장해 두고 재사용한다(데이터 캐시).
// 순위는 하루 한 번 바뀌므로 매 요청마다 백엔드를 부를 필요가 없다. 1시간이 지나면 다음 요청 때 새로 받아 온다
export const getCategories = () => request<Category[]>("/api/categories", { next: { revalidate: HOUR } });

export const getRankings = (date?: string) =>
  request<CategoryRanking[]>(`/api/rankings${dateQuery(date)}`, { next: { revalidate: HOUR } });

export const getCategoryRanking = (slug: string, date?: string) =>
  request<CategoryRanking>(`/api/rankings/${encodeURIComponent(slug)}${dateQuery(date)}`, {
    next: { revalidate: HOUR },
  });

export const getRepo = (owner: string, name: string) =>
  request<RepoDetail>(`/api/repos/${encodeURIComponent(owner)}/${encodeURIComponent(name)}`, {
    next: { revalidate: HOUR },
  });

export const getStars = (fullName: string, days = 30) =>
  request<StarPoint[]>(`/api/repos/${fullName}/stars?days=${days}`);

// ── 회원 (브라우저에서만) ──
// cache: "no-store": 로그인 정보는 사람마다 다르고 자주 바뀌므로 절대 캐시하지 않는다
/** 로그인 안 했으면 null */
export async function getMe(): Promise<Me | null> {
  try {
    return await request<Me>("/api/me", { cache: "no-store" });
  } catch (e) {
    // [문법] instanceof: e가 ApiError로 만든 객체인지 확인하고, 맞으면 e.status를 안전하게 쓸 수 있다(타입 좁히기)
    if (e instanceof ApiError && e.status === 401) return null;
    throw e;
  }
}

export const getBookmarks = () => request<Bookmark[]>("/api/bookmarks", { cache: "no-store" });
export const addBookmark = (repoId: number) => request<void>(`/api/bookmarks/${repoId}`, { method: "POST" });
export const removeBookmark = (repoId: number) => request<void>(`/api/bookmarks/${repoId}`, { method: "DELETE" });
export const logout = () => request<void>("/api/logout", { method: "POST" });

// ── 관리자 ──
export const getReviews = () => request<ReviewItem[]>("/api/admin/reviews", { cache: "no-store" });
export const submitReview = (id: number, body: ReviewRequest) =>
  request<void>(`/api/admin/reviews/${id}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body), // 자바스크립트 객체 → JSON 문자열
  });

// 이 주소로 이동하면 Spring Security가 GitHub 로그인 화면으로 보낸다(OAuth 흐름 시작)
export const LOGIN_URL = "/oauth2/authorization/github";
