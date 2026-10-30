"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { addBookmark, getBookmarks, getMe, LOGIN_URL, removeBookmark } from "@/lib/api";

/**
 * 상세 화면의 북마크 버튼. 비로그인이면 로그인 유도, 로그인이면 저장/해제 토글.
 *
 * [CS] 버튼을 누르는 동안(busy) 비활성화한다. 응답이 오기 전에 여러 번 누르면 같은 요청이 중복으로 나가
 * 서버 상태와 화면 상태가 어긋날 수 있기 때문이다(중복 제출 방지).
 */
export function BookmarkButton({ repoId }: { repoId: number }) {
  // undefined: 확인 중, null: 비로그인
  const [saved, setSaved] = useState<boolean | null | undefined>(undefined);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    getMe()
      .then(async (me) => {
        if (!me) return setSaved(null);
        const list = await getBookmarks();
        // some: 배열에 조건을 만족하는 원소가 하나라도 있으면 true
        setSaved((list ?? []).some((b) => b.repoId === repoId));
      })
      .catch(() => setSaved(null));
  }, [repoId]);

  if (saved === undefined) return <Button variant="outline" disabled>북마크</Button>;

  if (saved === null) {
    return (
      <Button variant="outline" onClick={() => (window.location.href = LOGIN_URL)}>
        로그인하고 북마크
      </Button>
    );
  }

  return (
    <Button
      variant={saved ? "default" : "outline"}
      disabled={busy}
      aria-pressed={saved} // 토글 버튼의 눌림 상태를 스크린 리더에 알린다(접근성)
      onClick={async () => {
        setBusy(true);
        // [문법] try/finally: 요청이 성공하든 실패하든 finally는 반드시 실행돼 버튼이 다시 켜진다
        try {
          await (saved ? removeBookmark(repoId) : addBookmark(repoId));
          setSaved(!saved); // 서버가 성공한 뒤에만 화면을 바꾼다(서버와 화면 상태 일치)
        } finally {
          setBusy(false);
        }
      }}
    >
      {saved ? "★ 북마크됨" : "☆ 북마크"}
    </Button>
  );
}
