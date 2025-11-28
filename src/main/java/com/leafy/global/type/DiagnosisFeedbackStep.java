package com.leafy.global.type;

public enum DiagnosisFeedbackStep {
    NONE,           // 초기 상태
    TIP_SENT,       // D+2 팁 발송 완료
    CHECK_REQUESTED, // D+5 상태 확인 요청 발송 완료
    COMPLETED       // 유저가 피드백 완료 (루프 종료)
}
