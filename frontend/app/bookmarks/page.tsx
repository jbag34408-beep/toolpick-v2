"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import { getBookmarks, getMe, LOGIN_URL, removeBookmark, type Bookmark } from "@/lib/api";

/**
 * 내 북마크 (/bookmarks). 사람마다 내용이 달라 서버에서 캐시할 수 없으므로 클라이언트 컴포넌트로 만든다.
 *
 * [문법] 구별된 유니언(discriminated union): kind 값으로 상태를 구분한다.
 * kind가 "ready"일 때만 items가 있다고 TypeScript가 알기 때문에, state.kind === "ready" 검사 뒤에서만 state.items를 쓸 수 있다.
 * "로딩 중인데 items를 읽는" 같은 불가능한 상태를 타입 단계에서 막는다.
 */
type State = { kind: "loading" } | { kind: "anonymous" } | { kind: "error" } | { kind: "ready"; items: Bookmark[] };

export default function BookmarksPage() {
  const [state, setState] = useState<State>({ kind: "loading" });

  useEffect(() => {
    // [문법] 즉시 실행 async 함수 (async () => {...})(): useEffect 콜백 자체는 async일 수 없어 안에서 만들어 바로 부른다
    (async () => {
      const me = await getMe();
      if (!me) return setState({ kind: "anonymous" });
      setState({ kind: "ready", items: (await getBookmarks()) ?? [] });
    })().catch(() => setState({ kind: "error" }));
  }, []);

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">내 북마크</h1>
      {state.kind === "loading" && <p className="text-sm text-muted-foreground">불러오는 중…</p>}
      {state.kind === "error" && <p className="text-sm text-muted-foreground">북마크를 불러오지 못했습니다.</p>}
      {state.kind === "anonymous" && (
        <div className="space-y-3 glass rounded-2xl p-6 text-center">
          <p className="text-sm text-muted-foreground">로그인하면 관심 있는 도구를 저장해 둘 수 있습니다.</p>
          <a href={LOGIN_URL} className={buttonVariants()}>
            GitHub로 로그인
          </a>
        </div>
      )}
      {state.kind === "ready" &&
        (state.items.length === 0 ? (
          <p className="text-sm text-muted-foreground">아직 저장한 도구가 없습니다. 도구 상세 화면에서 북마크해 보세요.</p>
        ) : (
          <ul className="glass divide-y divide-black/5 rounded-2xl">
            {state.items.map((b) => (
              <li key={b.repoId} className="flex items-center gap-3 px-4 py-3">
                <Link href={`/repo/${b.fullName}`} className="min-w-0 flex-1 hover:underline">
                  <span className="block truncate font-semibold">{b.fullName}</span>
                  <span className="block text-sm text-muted-foreground">{b.summaryKo ?? "요약 없음"}</span>
                </Link>
                {b.language && <Badge variant="secondary">{b.language}</Badge>}
                <button
                  type="button"
                  className={buttonVariants({ variant: "ghost", size: "sm" })}
                  onClick={async () => {
                    await removeBookmark(b.repoId);
                    // [CS] 불변 업데이트: 기존 배열을 고치지 않고 filter로 새 배열을 만들어 넘긴다.
                    // React는 "새 객체인지"로 변경을 알아채므로 원본을 직접 바꾸면 화면이 갱신되지 않을 수 있다
                    setState({ kind: "ready", items: state.items.filter((x) => x.repoId !== b.repoId) });
                  }}
                >
                  삭제
                </button>
              </li>
            ))}
          </ul>
        ))}
    </div>
  );
}
