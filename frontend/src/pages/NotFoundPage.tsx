import { Button } from '@/components';

function NotFoundPage() {
  return (
    <div className="mx-auto flex max-w-page flex-col items-center gap-4 px-6 py-32 text-center">
      <h1 className="text-3xl font-semibold text-heading">
        페이지를 찾을 수 없어요
      </h1>
      <p className="text-ink-soft">
        주소가 잘못됐거나 페이지가 옮겨졌을 수 있어요.
      </p>
      <Button to="/" size="lg">
        메인으로 가기
      </Button>
    </div>
  );
}

export default NotFoundPage;
