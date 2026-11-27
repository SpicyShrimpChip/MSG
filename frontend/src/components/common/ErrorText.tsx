interface ErrorTextProps {
  // 뮤테이션의 error를 그대로 넘긴다. 오류가 없으면 아무것도 그리지 않는다
  error?: Error | null;
  // Error가 아닌 문구(막힌 이유 등)를 보여 줄 때. error가 있으면 error.message가 먼저다
  message?: string | null;
}

// 폼·버튼 동작 실패 문구. 화면 낭독기가 바로 읽도록 role="alert"를 붙인다
function ErrorText({ error, message }: ErrorTextProps) {
  if (!error && !message) return null;
  return (
    <p role="alert" className="text-body text-danger">
      {error ? error.message : message}
    </p>
  );
}

export default ErrorText;
