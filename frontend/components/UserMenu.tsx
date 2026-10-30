"use client";
// [문법] "use client": 이 파일의 컴포넌트는 브라우저에서도 실행되는 클라이언트 컴포넌트다.
// useState·useEffect 같은 훅(hook)과 onClick 같은 이벤트는 클라이언트 컴포넌트에서만 쓸 수 있다.

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { buttonVariants } from "@/components/ui/button";
import { getMe, logout, LOGIN_URL, type Me } from "@/lib/api";

/**
 * 헤더 오른쪽 로그인 메뉴. 로그인 전에는 "GitHub 로그인" 버튼, 로그인 후에는 아바타·북마크·로그아웃.
 *
 * [CS] 로그인 정보는 사람마다 다르므로 서버에서 캐시한 HTML에 넣지 않고, 브라우저에서 따로 /api/me를 부른다.
 * 페이지 본문(순위)은 모두에게 같아서 캐시하고, 개인 정보만 나중에 채운다.
 */
export function UserMenu() {
  const router = useRouter();
  // [문법] useState: 컴포넌트가 기억하는 값. 값이 바뀌면(setMe) React가 화면을 다시 그린다.
  // 세 가지 상태를 하나로 표현한다: undefined = 아직 확인 중, null = 비로그인, Me = 로그인함
  const [me, setMe] = useState<Me | null | undefined>(undefined);

  // [문법] useEffect(함수, []): 화면에 처음 나타난 뒤 한 번 실행한다(두 번째 인자 []가 "한 번만"이라는 뜻).
  // 데이터 요청 같은 부수 효과(side effect)는 렌더링 중이 아니라 여기서 한다
  useEffect(() => {
    getMe().then(setMe).catch(() => setMe(null));
  }, []);

  // 확인 중에는 같은 크기의 빈 자리를 두어 버튼이 나타날 때 레이아웃이 덜컹거리지 않게 한다(레이아웃 이동 방지)
  if (me === undefined) return <span className="h-8 w-20" aria-hidden />;

  if (!me) {
    return (
      // 로그인은 페이지 전체 이동이어야 해서(<a>) Link를 쓰지 않는다: 백엔드가 GitHub으로 리다이렉트한다
      <a href={LOGIN_URL} className={buttonVariants({ size: "sm" })}>
        GitHub 로그인
      </a>
    );
  }

  return (
    <div className="flex items-center gap-1">
      {/* 관리자 메뉴는 "보여줄지"만 여기서 정한다. 실제 차단은 서버(SecurityConfig)가 한다 — 화면 숨김은 보안이 아니다 */}
      {me.role === "ADMIN" && (
        <Link href="/admin/reviews" className={buttonVariants({ variant: "ghost", size: "sm" })}>
          검수
        </Link>
      )}
      <Link href="/bookmarks" className={buttonVariants({ variant: "ghost", size: "sm" })}>
        북마크
      </Link>
      <span className="flex items-center gap-2">
        {me.avatarUrl && (
          // eslint-disable-next-line @next/next/no-img-element -- GitHub 아바타는 외부 이미지라 최적화 설정 없이 그대로 쓴다
          <img src={me.avatarUrl} alt="" className="size-6 rounded-full" />
        )}
        <span className="font-medium">{me.login}</span>
      </span>
      <button
        type="button"
        className={buttonVariants({ variant: "ghost", size: "sm" })}
        // [문법] async 화살표 함수: 로그아웃 요청이 끝날 때까지 기다린 뒤 화면을 바꾼다
        onClick={async () => {
          await logout().catch(() => undefined);
          setMe(null);
          router.push("/");
          router.refresh(); // 서버 컴포넌트를 다시 받아 로그인 상태가 남은 화면을 지운다
        }}
      >
        로그아웃
      </button>
    </div>
  );
}
