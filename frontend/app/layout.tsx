import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { Header } from "@/components/Header";
import "./globals.css";

/**
 * 루트 레이아웃: 모든 페이지를 감싸는 공통 틀(<html>, <body>, 헤더, 푸터).
 *
 * [문법] App Router 규칙: app/ 폴더 구조가 곧 URL이다. layout.tsx는 그 폴더와 하위 페이지 전부를 감싸고,
 * page.tsx가 실제 화면이다. children 자리에 각 페이지가 들어간다.
 * 페이지를 이동해도 레이아웃은 다시 그려지지 않고 children만 바뀐다(헤더 상태 유지).
 *
 * [문법] next/font: 빌드할 때 글꼴 파일을 받아 우리 서버에서 함께 제공한다. 외부 글꼴 서버에 요청하지 않아
 * 빠르고, 글꼴이 늦게 바뀌며 화면이 흔들리는 현상(layout shift)을 줄인다. variable은 CSS 변수 이름이다.
 */
const geistSans = Geist({
  variable: "--font-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

// [CS] metadata는 <head>의 <title>, <meta description>이 된다. 검색 결과·링크 미리보기에 쓰인다(SEO).
// template: 하위 페이지가 title을 "acme/ai-cli"로 정하면 "acme/ai-cli | 툴픽"이 된다
export const metadata: Metadata = {
  title: { default: "툴픽 — 오늘 쓸 만한 깃허브 도구 Top 10", template: "%s | 툴픽" },
  description: "매일 깃허브에서 뜨는 레포 중 바로 설치해 쓸 수 있는 도구만 AI로 골라 카테고리별 Top 10으로 보여줍니다.",
};

// LayoutProps<"/">: Next.js가 경로별로 자동 생성해 주는 props 타입
export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    // lang="ko": 화면 낭독기가 한국어로 읽고, 브라우저가 맞는 글꼴·줄바꿈 규칙을 쓴다
    <html lang="ko" className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}>
      <body className="flex min-h-full flex-col bg-background text-foreground">
        <Header />
        {/* flex-1: 본문이 남은 높이를 모두 차지해 내용이 짧아도 푸터가 화면 맨 아래에 붙는다 */}
        <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-6">{children}</main>
        <footer className="border-t py-4 text-center text-xs text-muted-foreground">
          툴픽 · 매일 새벽(KST) 갱신 · 데이터 출처 GitHub
        </footer>
      </body>
    </html>
  );
}
