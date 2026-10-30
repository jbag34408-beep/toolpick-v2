import type { Metadata } from "next";
import { CopyButton } from "@/components/CopyButton";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

/**
 * AI 연결 안내 (/mcp): MCP 서버 주소와 Claude·Cursor 설정 예시.
 *
 * [CS] 데이터 요청이 없는 정적 페이지라 빌드할 때 HTML을 한 번 만들어 두고 그대로 보낸다(SSG). 가장 빠르다.
 * BACKEND_URL은 빌드 시점 환경 변수 값으로 HTML에 박힌다.
 */
export const metadata: Metadata = { title: "AI 연결 (MCP)" };

// as const 없이도 충분한 단순 상수 배열. 화면 아래 "제공하는 도구" 목록에 쓴다
const TOOLS = [
  { name: "getTopTools", desc: "카테고리별 오늘의 도구 Top 10 (category를 비우면 전체)" },
  { name: "getToolDetail", desc: "도구의 한국어 요약, 설치 명령, 최근 30일 스타 추이 (fullName: owner/name)" },
  { name: "listCategories", desc: "카테고리 목록과 slug" },
];

export default function McpPage() {
  // MCP는 rewrite 없이 백엔드에 직접 붙는다
  // (AI 클라이언트는 브라우저가 아니라 쿠키·CORS가 필요 없고, 프론트 서버를 거치면 한 단계 느려질 뿐이다)
  const mcpUrl = `${process.env.BACKEND_URL ?? "http://localhost:8080"}/mcp`;

  // JSON.stringify(값, null, 2): 들여쓰기 2칸으로 보기 좋게 JSON 문자열을 만든다
  const examples = [
    { title: "Claude Code", code: `claude mcp add --transport http toolpick ${mcpUrl}` },
    {
      title: "Cursor (~/.cursor/mcp.json)",
      code: JSON.stringify({ mcpServers: { toolpick: { url: mcpUrl } } }, null, 2),
    },
    {
      // Claude Desktop 설정 파일은 원격 URL을 직접 못 받아서 mcp-remote(로컬 ↔ 원격 중계 프로그램)를 거친다
      title: "Claude Desktop (claude_desktop_config.json)",
      code: JSON.stringify({ mcpServers: { toolpick: { command: "npx", args: ["-y", "mcp-remote", mcpUrl] } } }, null, 2),
    },
  ];

  return (
    <div className="space-y-6">
      <section className="space-y-2">
        <h1 className="text-2xl font-bold">AI에서 툴픽 쓰기</h1>
        <p className="text-sm text-muted-foreground">
          {/* &ldquo; &rdquo;: JSX 안에서 큰따옴표를 그대로 쓰면 린트가 경고하므로 HTML 엔티티(“ ”)로 쓴다 */}
          Claude·ChatGPT·Cursor 같은 AI에 툴픽 MCP 서버를 등록하면 &ldquo;요즘 뜨는 CLI 도구 알려줘&rdquo;처럼 물었을 때 툴픽 순위와 요약을 바로 가져옵니다.
          로그인 없이 쓸 수 있습니다.
        </p>
      </section>

      <Card>
        <CardHeader>
          <CardTitle>MCP 서버 주소 (Streamable HTTP)</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex items-center gap-2 rounded-full bg-black/5 py-1.5 pr-1.5 pl-4">
            <code className="flex-1 overflow-x-auto font-mono text-sm whitespace-nowrap">{mcpUrl}</code>
            <CopyButton text={mcpUrl} />
          </div>
          <p className="mt-2 text-xs text-muted-foreground">
            무료 서버라 한동안 요청이 없으면 잠들어 있다가 첫 호출 때 깨어나는 데 1분 가까이 걸릴 수 있습니다.
          </p>
        </CardContent>
      </Card>

      {examples.map((ex) => (
        <Card key={ex.title}>
          <CardHeader className="flex flex-row items-center justify-between">
            <CardTitle>{ex.title}</CardTitle>
            <CopyButton text={ex.code} />
          </CardHeader>
          <CardContent>
            {/* <pre>: 공백·줄바꿈을 그대로 보여준다(JSON 들여쓰기 유지) */}
            <pre className="overflow-x-auto rounded-xl bg-black/5 p-3 font-mono text-sm">{ex.code}</pre>
          </CardContent>
        </Card>
      ))}

      <Card>
        <CardHeader>
          <CardTitle>제공하는 도구</CardTitle>
        </CardHeader>
        <CardContent>
          <ul className="space-y-2 text-sm">
            {TOOLS.map((t) => (
              <li key={t.name}>
                <code className="font-mono font-semibold">{t.name}</code>
                <span className="text-muted-foreground"> — {t.desc}</span>
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>
    </div>
  );
}
