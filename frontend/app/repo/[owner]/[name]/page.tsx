import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { BookmarkButton } from "@/components/BookmarkButton";
import { CopyButton } from "@/components/CopyButton";
import { StarChart } from "@/components/StarChart";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { getRepo } from "@/lib/api";

/**
 * 도구 상세 (/repo/acme/ai-cli). 폴더 [owner]/[name] 두 단계가 URL 두 구간을 받는다.
 *
 * [CS] 서버 컴포넌트와 클라이언트 컴포넌트를 섞는다: 본문(요약·설치 명령)은 서버에서 HTML로 그리고,
 * 상호작용이 필요한 부분(BookmarkButton, CopyButton, StarChart)만 클라이언트 컴포넌트로 끼워 넣는다.
 * 브라우저로 내려가는 JS가 그 세 컴포넌트뿐이라 페이지가 가볍다(부분 하이드레이션).
 * 하이드레이션(hydration): 서버가 만든 HTML에 브라우저 JS가 이벤트 처리기를 붙여 "살아 있는" 화면으로 만드는 과정.
 */
export async function generateMetadata({ params }: PageProps<"/repo/[owner]/[name]">): Promise<Metadata> {
  const { owner, name } = await params;
  const repo = await getRepo(owner, name).catch(() => null);
  return repo
    ? { title: repo.fullName, description: repo.summaryKo ?? repo.description ?? undefined }
    : { title: "도구" };
}

export default async function RepoPage({ params }: PageProps<"/repo/[owner]/[name]">) {
  const { owner, name } = await params;
  const repo = await getRepo(owner, name);
  if (!repo) notFound(); // 검수 전·거절된 레포도 백엔드가 404를 줘서 여기로 온다

  return (
    <div className="space-y-6">
      <section className="space-y-3">
        <div className="flex flex-wrap items-center gap-2 text-sm">
          {repo.categorySlug && (
            <Link href={`/category/${repo.categorySlug}`}>
              <Badge variant="outline">{repo.categoryNameKo}</Badge>
            </Link>
          )}
          {repo.language && <Badge variant="secondary">{repo.language}</Badge>}
          {repo.license && <Badge variant="secondary">{repo.license}</Badge>}
        </div>
        {/* break-all: 띄어쓰기 없는 긴 레포 이름이 화면 밖으로 넘치지 않게 아무 곳에서나 줄바꿈 */}
        <h1 className="text-2xl font-bold break-all">{repo.fullName}</h1>
        <p className="text-lg">{repo.summaryKo ?? repo.description}</p>
        {repo.summaryKo && repo.description && <p className="text-sm text-muted-foreground">{repo.description}</p>}
        <div className="flex flex-wrap items-center gap-2">
          {/* target="_blank"는 새 탭, rel="noreferrer"는 새 탭이 window.opener로 원래 페이지를 조작하지 못하게 막는다(보안) */}
          <a href={repo.htmlUrl} target="_blank" rel="noreferrer" className={buttonVariants()}>
            GitHub에서 보기
          </a>
          <BookmarkButton repoId={repo.id} />
          {repo.stars !== null && (
            <span className="text-sm text-muted-foreground">★ {repo.stars.toLocaleString()}</span>
          )}
        </div>
      </section>

      <Card>
        <CardHeader>
          <CardTitle>설치</CardTitle>
        </CardHeader>
        <CardContent>
          {/* [문법] 삼항 연산자로 두 가지 화면 중 하나를 고른다 */}
          {repo.installCmd ? (
            <div className="flex items-center gap-2 rounded-full bg-black/5 py-1.5 pr-1.5 pl-4">
              {/* <code>: 코드라는 의미의 태그. overflow-x-auto로 긴 명령은 가로 스크롤 */}
              <code className="flex-1 overflow-x-auto font-mono text-sm whitespace-nowrap">{repo.installCmd}</code>
              <CopyButton text={repo.installCmd} />
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">README에 설치 명령이 없습니다. GitHub에서 사용 방법을 확인하세요.</p>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>최근 30일 스타</CardTitle>
        </CardHeader>
        <CardContent>
          <StarChart fullName={repo.fullName} />
        </CardContent>
      </Card>
    </div>
  );
}
