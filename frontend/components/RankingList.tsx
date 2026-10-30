import { ToolCard } from "@/components/ToolCard";
import type { RankingItem } from "@/lib/api";

/**
 * 순위 목록. 비어 있으면 빈 상태 문구를 보여준다.
 * [CS] 빈 상태(empty state) 처리: 데이터가 없을 때 아무것도 안 보이면 사용자는 "고장 났나?"라고 생각한다.
 * 왜 비었는지 알려 주는 문구를 둔다. <ol>(순서 있는 목록)을 써서 순위라는 의미를 HTML 구조로도 드러낸다(시맨틱 마크업).
 */
export function RankingList({ items }: { items: RankingItem[] }) {
  if (items.length === 0) {
    return (
      <p className="px-3 py-6 text-center text-sm text-muted-foreground">
        아직 이 카테고리에 오른 도구가 없습니다.
      </p>
    );
  }
  return (
    <ol className="divide-y">
      {items.map((item) => (
        <li key={item.repoId}>
          <ToolCard item={item} />
        </li>
      ))}
    </ol>
  );
}
