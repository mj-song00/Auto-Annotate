# 🔵  Recoding Highlight 
> PDF 문서 자동 분석 및 Excel 리포트 생성 서비스
> 
> 주소 : [서비스 주소](https://recordhighlight.com/)

## 1. 프로젝트 소개
### 🔎 개요

보험 설계를 하면서 반복적으로 수행되는 문서 검토 작업을 자동화하기 위해 개발된 서비스 입니다. 
기존에는 사람이 직접 조건을 확인하며 검토해야 했던 과정을, 조건 기반 분석 및 리포트 자동 생성으로 단축했습니다.

### 개발 목적
- 수작업으로 진행되는 문서 검토 과정 자동화
- 반복 업무 감소 및 처리 속도 향상
- 정형화된 Excel 리포트 자동 생성


## 2. 주요 기능
📁 1) 문서 업로드
- 다중 PDF 업로드
- Folder 단위 관리
- S3 저장
- bundleKey 기반 그룹 처리

🧐 2) 조건 기반 PDF 분석
- Apache PDFBox 기반 텍스트 추출
- 특정 키워드 / 조건 탐색
- 하이라이트 처리된 결과 생성

📈 3) Excel 자동 생성
- Apache POI 사용
- 조건별 시트 분리
- 분석 결과 표 형태로 정리
- 실행 로그(ExcelJobLog) 저장

🔒 4) 인증 및 보안
- JWT 기반 인증
- Access Token + Refresh Token 구조
- Refresh Token은 HttpOnly Cookie로 관리
- 로그아웃 시 토큰 무효화 처리

## 🚨 3. 트러블 슈팅 
### 1. 500 에러도 아닌데 PDF가 렌더링되지 않던 문제

#### 문제상황
-  `GET /document/{id}/highlighted`요청 시 HTTP 200 OK였지만 PDF가 표시되지 않았음
-  서버 예외 로그 없음
- 로그는 applyHighlights()까지만 출력되고 이후 단계 로그 미출력
에러가 아닌 **응답 미완료 상태**

#### 원인
`generateHighlightedPdf()`  내부 구조가 다음과 같았다.
> (페이지 수) × (레코드 수) × (타입 수) 만큼
calculateTextPositions() 호출
→ 내부에서 PDFTextStripper.getText() 반복 실행

고비용 텍스트 추출 작업이 다중 루프 안에 위치해 있어
입력 규모가 커질수록 처리 시간이 급격히 증가하는 구조였다.

로그 측정 결과:

- 개선 전: generateHighlightedPdf ≈ 9756ms

#### 개선 범위
- 레코드가 존재하는 페이지 기준으로 처리 범위 축소
- 페이지 내 동일 텍스트 위치 계산 캐싱
- 공백 정규화 기준을 통일해 인덱스 정합성 개선
- 단계별 START/END 로그 및 elapsedMs 도입

#### 개선 후 
- 개선 후: generateHighlightedPdf ≈ 5000~6000ms
- PDF 미표시 현상 제거
- 생성 단계가 로그 기준 정상 종료됨 확인

#### 결과 
**약 9.8초 → 5~6초로 단축 (약 4~5초 감소, 40~50% 개선)**
------- 

### 2. JWT 인증 구조 단순화 (Redis 미도입 결정)
#### 문제
- 로그아웃/인증 만료 처리에서 “토큰 폐기”를 위해 Redis blacklist 도입을 고려했으나, 서비스 규모 대비 운영 복잡도와 비용 증가가 부담이었음.
#### 개선
- Redis 미도입, 인증 상태 관리를 RDB 단일 구조로 단순화
- Refresh Token 1개 정책 적용 (사용자당 1개 저장/갱신)
- 로그아웃 시: RDB에 저장된 Refresh Token 무효화 1건 처리
- Access Token 만료: 15분(짧은 만료로 탈취 리스크 제한)

#### 결과
- 운영 컴포넌트: 2개(Redis + RDB) → 1개(RDB) 로 단순화
- 추가 인프라 비용: Redis 0원
- 토큰 폐기 처리: 로그아웃 시 DB 1건 무효화로 일관되게 처리

## 4. 시스템 아키텍처


## 5. 향후 개선 계획
- 멀티 인스턴스 환경에서 동시성 제어 개선
- Redis 도입 검토
- CI/CD 파이프라인 구축 

## 6. 기술 스택 
Backend

![](https://img.shields.io/badge/Spring-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![](https://img.shields.io/badge/Gradle-02303A.svg?style=for-the-badge&logo=Gradle&logoColor=white)
![](https://img.shields.io/badge/-Swagger-%23Clojure?style=for-the-badge&logo=swagger&logoColor=white)
![](https://img.shields.io/badge/springboot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![](https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)


Frontend

![](https://img.shields.io/badge/Thymeleaf-005F0F?style=for-the-badge&logo=thymeleaf&logoColor=white)
![](https://img.shields.io/badge/HTML-239120?style=for-the-badge&logo=html5&logoColor=white)
![](https://img.shields.io/badge/javascript-F7DF1E?style=for-the-badge&logo=javascript&logoColor=white)


Infra


![](https://img.shields.io/badge/AWS%20Elastic%20Beanstalk-FF9900?style=for-the-badge&logo=amazonaws&logoColor=white)
![](https://img.shields.io/badge/AWS%20RDS-527FFF?style=for-the-badge&logo=amazonrds&logoColor=white)
![](https://img.shields.io/badge/AWS%20S3-569A31?style=for-the-badge&logo=amazons3&logoColor=white)
![](https://img.shields.io/badge/Nginx-009639?style=for-the-badge&logo=nginx&logoColor=white)


version control


![](https://img.shields.io/badge/GitHub-100000?style=for-the-badge&logo=github&logoColor=white)
![](https://img.shields.io/badge/git-F05032?style=for-the-badge&logo=git&logoColor=white)


 
