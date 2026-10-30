"use client";

import { useEffect, useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { getCategories, getMe, getReviews, submitReview, type Category, type ReviewItem } from "@/lib/api";

/**
 * 관리자 검수 (/admin/reviews): AI가 확신하지 못한 결과를 승인·거절하고 카테고리·요약을 고친다.
 *
 * [CS] 이 화면의 "관리자만 볼 수 있음" 처리는 사용자 경험을 위한 것일 뿐이다. 진짜 보안은 서버가 /api/admin/** 을
 * ROLE_ADMIN에게만 허용하는 것이다. 브라우저 코드는 누구나 고칠 수 있으므로 클라이언트 검사를 믿으면 안 된다.
 */
type State = { kind: "loading" } | { kind: "denied" } | { kind: "error" } | { kind: "ready"; items: ReviewItem[] };

export default function AdminReviewsPage() {
  const [state, setState] = useState<State>({ kind: "loading" });
  const [categories, setCategories] = useState<Category[]>([]);

  useEffect(() => {
    (async () => {
      const me = await getMe();
      if (me?.role !== "ADMIN") return setState({ kind: "denied" });
      // [CS] Promise.all: 서로 상관없는 두 요청을 동시에(병렬로) 보내고 둘 다 끝날 때까지 기다린다.
      // 하나씩 차례로 기다리면 두 응답 시간의 합만큼, 동시에 보내면 더 느린 쪽 시간만큼 걸린다
      const [items, cats] = await Promise.all([getReviews(), getCategories()]);
      setCategories(cats ?? []);
      setState({ kind: "ready", items: items ?? [] });
    })().catch(() => setState({ kind: "error" }));
  }, []);

  // 이른 반환(early return)으로 상태별 화면을 먼저 처리하면 아래 본문은 "ready"인 경우만 생각하면 된다
  if (state.kind === "loading") return <p className="text-sm text-muted-foreground">불러오는 중…</p>;
  if (state.kind === "denied") return <p className="py-16 text-center text-sm text-muted-foreground">관리자만 볼 수 있는 화면입니다.</p>;
  if (state.kind === "error") return <p className="text-sm text-muted-foreground">검수 목록을 불러오지 못했습니다.</p>;

  // 처리한 항목을 목록에서 뺀다 (서버에 다시 묻지 않고 화면만 갱신)
  const done = (id: number) => setState({ kind: "ready", items: state.items.filter((x) => x.id !== id) });

  return (
    <div className="space-y-4">
      <div className="space-y-1">
        <h1 className="text-2xl font-bold">검수 대기 {state.items.length}건</h1>
        <p className="text-sm text-muted-foreground">AI 확신도가 낮거나 응답을 해석하지 못한 결과입니다. 결과는 다음 배치 순위부터 반영됩니다.</p>
      </div>
      {state.items.length === 0 ? (
        <p className="text-sm text-muted-foreground">검수할 항목이 없습니다.</p>
      ) : (
        <ul className="space-y-3">
          {state.items.map((item) => (
            <ReviewCard key={item.id} item={item} categories={categories} onDone={() => done(item.id)} />
          ))}
        </ul>
      )}
    </div>
  );
}

/**
 * 검수 항목 하나. 입력값(카테고리·요약)을 카드마다 따로 기억해야 해서 별도 컴포넌트로 나눴다.
 * [문법] 제어 컴포넌트(controlled component): input의 value를 상태(useState)와 묶고, 입력할 때마다 onChange로 상태를 바꾼다.
 * 화면에 보이는 값과 상태가 항상 같아서 제출할 때 상태만 읽으면 된다.
 * [문법] onDone처럼 함수를 props로 넘겨 자식이 부모에게 "끝났다"고 알린다(콜백, 상태 끌어올리기).
 */
function ReviewCard({ item, categories, onDone }: { item: ReviewItem; categories: Category[]; onDone: () => void }) {
  const [category, setCategory] = useState(item.categorySlug ?? "");
  const [summary, setSummary] = useState(item.summaryKo ?? "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function decide(decision: "APPROVE" | "REJECT") {
    setBusy(true);
    setError(null);
    try {
      // 빈 문자열이면 undefined로 바꿔 JSON에서 빠지게 한다 → 서버는 "AI 결과 그대로"로 처리
      await submitReview(item.id, { decision, categorySlug: category || undefined, summaryKo: summary || undefined });
      onDone();
    } catch {
      setError("저장하지 못했습니다. 승인하려면 카테고리를 골라야 합니다.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <li className="glass space-y-3 rounded-2xl p-4">
      <div className="flex flex-wrap items-center gap-2">
        <a href={item.htmlUrl} target="_blank" rel="noreferrer" className="font-semibold hover:underline">
          {item.fullName}
        </a>
        {/* toFixed(2): 소수 둘째 자리까지 문자열로 (0.6 → "0.60") */}
        <Badge variant="secondary">확신도 {Number(item.confidence).toFixed(2)}</Badge>
        <Badge variant={item.useful ? "default" : "outline"}>{item.useful ? "AI: 쓸 만함" : "AI: 제외"}</Badge>
      </div>
      {item.description && <p className="text-sm text-muted-foreground">{item.description}</p>}
      {/* 넓은 화면에서는 [180px | 나머지] 2열, 좁은 화면에서는 1열 */}
      <div className="grid gap-2 sm:grid-cols-[180px_1fr]">
        <label className="space-y-1 text-sm">
          <span className="text-muted-foreground">카테고리</span>
          <select
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            className="h-9 w-full rounded-full border border-white/70 bg-white/60 px-3 backdrop-blur-sm"
          >
            <option value="">선택</option>
            {categories.map((c) => (
              <option key={c.slug} value={c.slug}>
                {c.nameKo}
              </option>
            ))}
          </select>
        </label>
        <label className="space-y-1 text-sm">
          <span className="text-muted-foreground">한국어 요약</span>
          {/* maxLength=200: 서버 검증(@Size(max = 200))과 같은 제한을 화면에서도 걸어 미리 막는다 */}
          <input
            value={summary}
            maxLength={200}
            onChange={(e) => setSummary(e.target.value)}
            className="h-9 w-full rounded-full border border-white/70 bg-white/60 px-3 backdrop-blur-sm"
          />
        </label>
      </div>
      {/* <details>: 클릭하면 펼쳐지는 HTML 기본 요소. JS 없이 접기·펼치기가 된다 */}
      <details className="text-xs text-muted-foreground">
        <summary className="cursor-pointer">AI 원본 응답</summary>
        {/* React는 {} 안의 문자열을 자동으로 이스케이프해 HTML로 해석하지 않는다 → AI 응답에 <script>가 있어도 실행되지 않는다(XSS 방지) */}
        <pre className="mt-2 overflow-x-auto rounded-lg bg-black/5 p-2 whitespace-pre-wrap">{item.raw}</pre>
      </details>
      {error && <p className="text-sm text-destructive">{error}</p>}
      <div className="flex gap-2">
        <Button disabled={busy} onClick={() => decide("APPROVE")}>
          승인
        </Button>
        <Button variant="outline" disabled={busy} onClick={() => decide("REJECT")}>
          거절
        </Button>
      </div>
    </li>
  );
}
