import Link from "next/link";
import { buttonVariants } from "@/components/ui/button";

/**
 * 404 화면. 없는 주소이거나 페이지에서 notFound()를 부르면 이 화면이 나오고 HTTP 상태도 404가 된다.
 * [CS] 상태 코드 404를 정확히 주면 검색 엔진이 없는 페이지를 색인하지 않는다(200으로 "없음" 화면을 주면 안 되는 이유).
 */
export default function NotFound() {
  return (
    <div className="space-y-3 py-16 text-center">
      <h1 className="text-xl font-bold">찾을 수 없습니다</h1>
      <p className="text-sm text-muted-foreground">아직 툴픽이 고르지 않은 도구이거나 없는 주소입니다.</p>
      <Link href="/" className={buttonVariants({ variant: "outline" })}>
        홈으로
      </Link>
    </div>
  );
}
