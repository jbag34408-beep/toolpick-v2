"use client";

import { Button } from "@/components/ui/button";

/**
 * 페이지에서 예외가 나면 대신 보여주는 화면.
 * [CS] 오류 경계(error boundary): 한 부분의 오류가 앱 전체를 하얀 화면으로 만들지 않게 그 영역만 이 화면으로 바꾼다.
 * 백엔드가 잠에서 깨는 중이라 요청이 시간 초과된 경우가 가장 흔하다.
 * [문법] Next.js 규칙상 error.tsx는 클라이언트 컴포넌트여야 하고, reset()을 부르면 그 영역을 다시 렌더링해 본다.
 */
export default function Error({ reset }: { error: Error; reset: () => void }) {
  return (
    <div className="space-y-3 py-16 text-center">
      <h1 className="text-xl font-bold">잠시 문제가 생겼습니다</h1>
      <p className="text-sm text-muted-foreground">서버가 잠에서 깨는 중일 수 있습니다. 잠시 후 다시 시도해 주세요.</p>
      <Button onClick={reset}>다시 시도</Button>
    </div>
  );
}
