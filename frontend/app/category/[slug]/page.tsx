import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { RankingList } from "@/components/RankingList";
import { buttonVariants } from "@/components/ui/button";
import { getCategoryRanking } from "@/lib/api";
import { categoryVisual } from "@/lib/categoryVisuals";

/**
 * 카테고리 Top 10 (/category/ai-agent?date=2026-10-26).
 *
 * [문법] 동적 라우트: 폴더 이름 [slug]가 URL의 그 자리 값을 받는다. params.slug = "ai-agent".
 * Next.js 15+에서 params와 searchParams(?date=)는 Promise라서 await로 꺼낸다.
 */

// [CS] 정규식 ^\d{4}-\d{2}-\d{2}$ : "숫자4-숫자2-숫자2" 형식만 통과. 이상한 값은 백엔드로 보내지 않고 무시한다(입력 검증)
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

// generateMetadata: 페이지마다 다른 <title>을 만든다. 같은 fetch는 Next.js가 한 번만 실행해 결과를 공유한다(요청 중복 제거)
export async function generateMetadata({ params }: PageProps<"/category/[slug]">): Promise<Metadata> {
  const { slug } = await params;
  const ranking = await getCategoryRanking(slug).catch(() => null);
  return { title: ranking ? `${ranking.nameKo} Top 10` : "카테고리" };
}

export default async function CategoryPage({ params, searchParams }: PageProps<"/category/[slug]">) {
  const { slug } = await params;
  // [문법] 구조 분해 + 이름 바꾸기: searchParams의 date를 rawDate라는 이름으로 꺼낸다
  const { date: rawDate } = await searchParams;
  // ?date=a&date=b 처럼 배열로 올 수도 있어 문자열인지부터 확인한다
  const date = typeof rawDate === "string" && ISO_DATE.test(rawDate) ? rawDate : undefined;

  const ranking = await getCategoryRanking(slug, date);
  if (!ranking) notFound(); // 없는 카테고리 → not-found.tsx 화면 + HTTP 404

  const visual = categoryVisual(ranking.slug);

  return (
    <div className="space-y-4">
      {/* 카테고리 배너: 대표 이미지 위에 이름·소개 */}
      <section className="relative h-44 overflow-hidden rounded-2xl bg-muted md:h-56">
        {/* eslint-disable-next-line @next/next/no-img-element -- 장식용 외부 이미지 */}
        <img src={visual.image} alt="" className="absolute inset-0 size-full object-cover" />
        <div className="absolute inset-0 bg-gradient-to-t from-black/75 via-black/30 to-transparent" />
        <div className="absolute inset-x-0 bottom-0 space-y-1 p-5 text-white md:p-7">
          <p className="text-sm text-white/85">{visual.tagline}</p>
          <h1 className="text-3xl font-bold">{ranking.nameKo} Top 10</h1>
        </div>
      </section>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">
          {ranking.date ? `${ranking.date} 기준 · 최근 7일 스타 증가량 순` : "이 날짜의 순위가 없습니다."}
        </p>
        {/* 자바스크립트 없이 동작하는 날짜 선택 */}
        {/* [CS] HTML 기본 form(GET)은 입력값을 ?date=... 쿼리 문자열로 붙여 같은 주소로 이동한다.
            JS 없이도 동작하고, 결과 URL을 북마크·공유할 수 있다(점진적 향상, progressive enhancement) */}
        <form className="flex items-center gap-2" action={`/category/${ranking.slug}`}>
          {/* label의 htmlFor와 input의 id를 맞추면 글자를 눌러도 입력칸이 선택되고, 스크린 리더가 이름을 읽는다 */}
          <label htmlFor="date" className="text-sm text-muted-foreground">
            날짜
          </label>
          <input
            id="date"
            name="date"
            type="date"
            defaultValue={ranking.date ?? date}
            className="glass h-8 rounded-full px-3 text-sm"
          />
          <button type="submit" className={buttonVariants({ variant: "outline", size: "sm" })}>
            보기
          </button>
        </form>
      </div>
      <div className="glass rounded-2xl p-1">
        <RankingList items={ranking.items} />
      </div>
    </div>
  );
}
