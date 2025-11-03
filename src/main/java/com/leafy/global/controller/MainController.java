package com.leafy.global.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MainController {

    /**
     * home.html에서 API 테스트용으로 호출하는 엔드포인트이다. -> 로그인 정상적으로 됐는지 확인하려고
     * @return 홈페이지 텍스트
     */
    @GetMapping("/home")
    public String homePage() {
        return "백엔드 API 서버의 /home 응답: 홈페이지입니다.";
    }
}