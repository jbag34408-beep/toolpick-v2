import Link from "next/link";
import { connection } from "next/server";
import { CategoryAccordion } from "@/components/CategoryAccordion";
import { RankingList } from "@/components/RankingList";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { getRankings } from "@/lib/api";
import { categoryVisual } from "@/lib/categoryVisuals";

/**
 * 홈 (/): 소개 + 카테고리 이미지 아코디언 + 카테고리별 Top 3 카드.
 *
 * [CS] 렌더링 방식 비교
 * <ul>
 * - SSR(서버 사이드 렌더링): 요청마다 서버가 HTML을 완성해 보낸다. 첫 화면이 빠르고 검색 엔진이 내용을 읽는다.
 * - SSG(정적 생성): 빌드할 때 HTML을 미리 만든다. 가장 빠르지만 빌드 시점 데이터로 굳는다.
 * - CSR(클라이언트 렌더링): 빈 HTML을 보내고 브라우저 JS가 데이터를 받아 그린다. 검색 노출에 불리하다.
 * </ul>
 * 홈은 SSR이다. 순위 데이터는 fetch 캐시(1시간)에 두어 백엔드 호출은 1시간에 한 번뿐이다.
 * 아코디언만 마우스 반응이 필요해 클라이언트 컴포넌트로 분리했다.
 */
export default async function HomePage() {
  // 요청 시점에 렌더링하고 데이터는 fetch 캐시(1시간)로 아낀다. 빌드 때는 백엔드를 부르지 않는다
  // [문법] connection(): "이 페이지는 실제 요청이 들어왔을 때 그린다"고 Next.js에 알린다. 없으면 빌드 때 미리 그리려다
  // 백엔드에 접속하게 되고, 백엔드가 꺼져 있으면 빌드가 실패한다
  await connection();
  const rankings = (await getRankings()) ?? [];
  // find + ?.: 날짜가 있는 첫 카테고리를 찾고, 없으면 undefined → ?? null
  const date = rankings.find((r) => r.date)?.date ?? null;

  return (
    <div className="space-y-10">
      <section className="grid items-center gap-8 md:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
        <div className="space-y-5 text-center md:text-left">
          <h1 className="text-4xl leading-tight font-bold tracking-tight md:text-5xl">
            오늘 쓸 만한
            <br />
            깃허브 도구
          </h1>
          <p className="text-muted-foreground">
            매일 깃허브에서 뜨는 레포 중 바로 설치해 쓸 수 있는 도구만 AI가 골라 카테고리별 Top 10으로 보여줍니다.
          </p>
          <p className="text-sm text-muted-foreground">
            {date ? `${date} 기준 · 최근 7일 스타 증가량 순` : "첫 순위를 준비하고 있습니다. 스타 기록이 쌓이면 순위가 나타납니다."}
          </p>
          <div className="flex flex-wrap justify-center gap-2 md:justify-start">
            <a href="#top3" className={buttonVariants({ size: "lg" })}>
              오늘의 Top 보기
            </a>
            <Link href="/mcp" className={buttonVariants({ variant: "outline", size: "lg" })}>
              AI에서 쓰기 (MCP)
            </Link>
          </div>
        </div>
        {rankings.length > 0 && (
          <CategoryAccordion
            categories={rankings.map((r) => ({ slug: r.slug, nameKo: r.nameKo, count: r.items.length, ...categoryVisual(r.slug) }))}
          />
        )}
      </section>

      {/* 반응형 그리드: 기본 1열, sm(640px) 이상 화면에서 2열 */}
      <section id="top3" className="grid scroll-mt-28 gap-4 sm:grid-cols-2">
        {rankings.map((category) => (
          <Card key={category.slug}>
            <CardHeader className="flex flex-row items-center justify-between">
              <CardTitle className="flex items-center gap-2">
                {/* eslint-disable-next-line @next/next/no-img-element -- 장식용 작은 외부 이미지 */}
                <img src={categoryVisual(category.slug).image} alt="" loading="lazy" className="size-7 rounded-lg object-cover" />
                {category.nameKo}
              </CardTitle>
              <Link href={`/category/${category.slug}`} className={buttonVariants({ variant: "ghost", size: "sm" })}>
                전체 보기 →
              </Link>
            </CardHeader>
            <CardContent className="px-2">
              {/* slice(0, 3): 배열 앞 3개만 (원본 배열은 바뀌지 않는다) */}
              <RankingList items={category.items.slice(0, 3)} />
            </CardContent>
          </Card>
        ))}
      </section>
    </div>
  );
}
