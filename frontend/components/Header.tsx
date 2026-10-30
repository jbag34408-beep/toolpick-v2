import Link from "next/link";
import { getCategories, type Category } from "@/lib/api";
import { UserMenu } from "@/components/UserMenu";
import { buttonVariants } from "@/components/ui/button";

/**
 * 모든 페이지 위에 붙는 머리말: 로고, AI 연결 링크, 로그인 메뉴, 카테고리 탭.
 *
 * [CS] 서버 컴포넌트(React Server Component): "use client"가 없는 컴포넌트는 서버에서만 실행되고
 * 완성된 HTML만 브라우저로 간다. 자바스크립트가 브라우저로 내려가지 않아 가볍고, 검색 엔진도 내용을 바로 읽는다(SEO).
 * 그래서 async 함수로 만들어 렌더링 중에 바로 데이터를 await할 수 있다.
 * 반대로 클릭·상태(useState)가 필요한 로그인 메뉴는 클라이언트 컴포넌트(UserMenu)로 분리했다.
 *
 * [문법] Link(next/link): <a> 태그를 감싼 것. 페이지 전체를 다시 받지 않고 필요한 부분만 바꿔 빠르게 이동한다(클라이언트 내비게이션).
 * "@/lib/api"의 @는 프로젝트 루트를 가리키는 경로 별칭(tsconfig.json의 paths)이다.
 */
export async function Header() {
  // 빌드 시점처럼 백엔드에 닿지 않을 때도 화면은 떠야 하므로 탭만 비운다
  // [문법] .catch(() => null): Promise가 실패하면 null로 바꾼다. ?? []: 결과가 null이면 빈 배열
  const categories: Category[] = await getCategories().catch(() => null) ?? [];

  // [문법] JSX: 자바스크립트 안에 HTML처럼 생긴 문법. class 대신 className을 쓴다.
  // className의 값들은 Tailwind CSS 유틸리티 클래스(border-b = 아래 테두리, px-4 = 좌우 여백 1rem ...)다
  return (
    // sticky + glass: 스크롤해도 위에 붙어 있고, 아래 내용이 흐리게 비친다
    <header className="glass sticky top-0 z-30 rounded-none border-x-0 border-t-0">
      <div className="mx-auto flex max-w-5xl items-center justify-between gap-4 px-4 py-3">
        <Link href="/" className="text-xl font-bold tracking-tight">
          툴픽
        </Link>
        <nav className="flex items-center gap-3 text-sm">
          <Link href="/mcp" className={buttonVariants({ variant: "ghost", size: "sm" })}>
            AI 연결
          </Link>
          <UserMenu />
        </nav>
      </div>
      {/* [문법] 조건부 렌더링: 앞이 참일 때만 && 뒤의 JSX를 그린다 */}
      {categories.length > 0 && (
        // aria-label: 화면 낭독기(스크린 리더) 사용자에게 이 내비게이션이 무엇인지 알려 준다(접근성)
        <nav aria-label="카테고리" className="mx-auto max-w-5xl overflow-x-auto px-4 pb-2">
          <ul className="flex gap-1 text-sm whitespace-nowrap">
            {/* [문법] 배열.map으로 목록을 그린다. key는 React가 어떤 항목이 바뀌었는지 구분하는 고유값(바뀌지 않는 slug 사용) */}
            {categories.map((c) => (
              <li key={c.slug}>
                <Link
                  href={`/category/${c.slug}`}
                  className="block rounded-full px-3 py-1.5 text-muted-foreground transition-colors hover:bg-white/50 hover:text-foreground"
                >
                  {c.nameKo}
                </Link>
              </li>
            ))}
          </ul>
        </nav>
      )}
    </header>
  );
}
