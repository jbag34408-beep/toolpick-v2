"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";

/**
 * 설치 명령·MCP 주소 복사 버튼.
 * [CS] navigator.clipboard는 브라우저 API라 클라이언트 컴포넌트에서만 쓸 수 있고, 보안상 HTTPS(또는 localhost)에서만 동작한다.
 * 복사 후 1.5초 동안 "복사됨"을 보여 주어 동작했다는 피드백을 준다.
 */
export function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <Button
      variant="outline"
      size="sm"
      onClick={async () => {
        await navigator.clipboard.writeText(text);
        setCopied(true);
        setTimeout(() => setCopied(false), 1500); // 1500ms 뒤에 원래 문구로
      }}
    >
      {copied ? "복사됨" : "복사"}
    </Button>
  );
}
