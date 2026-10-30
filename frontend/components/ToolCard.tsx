import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import type { RankingItem } from "@/lib/api";

/**
 * 순위 한 줄: 순위·이름·한 줄 요약·+스타 증가량·언어.
 *
 * [문법] 컴포넌트는 props(속성) 객체를 받는 함수다. { item }: { item: RankingItem } 은
 * "props에서 item을 꺼내고(구조 분해), 그 타입은 RankingItem"이라는 뜻이다.
 * [CS] 프레젠테이셔널 컴포넌트: 데이터를 받아 그리기만 하고 상태·요청이 없다. 그래서 서버 컴포넌트로 남아 JS가 내려가지 않는다.
 */
export function ToolCard({ item }: { item: RankingItem }) {
  return (
    <Link
      href={`/repo/${item.fullName}`}
      className="flex items-start gap-3 rounded-lg px-3 py-3 transition-colors hover:bg-white/50"
    >
      {/* tabular-nums: 숫자 폭을 같게 해 순위·증가량 숫자가 세로로 가지런히 정렬된다 */}
      <span className="w-6 shrink-0 text-right text-lg font-bold text-muted-foreground tabular-nums">{item.rank}</span>
      {/* min-w-0 + truncate: 긴 이름이 넘치면 줄바꿈 대신 "..."으로 자른다 (flex 자식은 기본 최소 폭이 내용 길이라 min-w-0이 필요) */}
      <span className="min-w-0 flex-1">
        <span className="block truncate font-semibold">{item.fullName}</span>
        <span className="block text-sm text-muted-foreground">{item.summaryKo ?? "요약 준비 중"}</span>
      </span>
      <span className="flex shrink-0 flex-col items-end gap-1">
        {/* toLocaleString: 1234 → "1,234" 처럼 천 단위 구분 기호를 넣는다 */}
        <span className="text-sm font-semibold text-emerald-600 tabular-nums dark:text-emerald-400">
          +{item.starGain.toLocaleString()}★
        </span>
        {item.language && <Badge variant="secondary">{item.language}</Badge>}
      </span>
    </Link>
  );
}
