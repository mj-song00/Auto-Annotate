# 🔵  Recoding Highlight 
> PDF 문서 자동 분석 및 Excel 리포트 생성 서비스
> 
> 주소 : [서비스 주소](https://recordhighlight.com/)

## 1. 프로젝트 소개
### 🔎 개요


보험 설계를 하면서 반복적으로 수행되는 문서 검토 작업을 자동화하기 위해 개발된 서비스 입니다. 
사용자가 PDF문서를 업로드하면 조건에 따라 문서를 분석하고 하이라이트 처리 후 Excel 리포트를 다운 받을 수 있습니다. 


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

## 3. 트러블 슈팅 

## 4. 시스템 아키텍처

## 5. 보안 및 설계 고민

## 6. 향후 개선 계획
- 멀티 인스턴스 환경에서 동시성 제어 개선
- Redis 도입 검토
- CI/CD 파이프라인 구축 

## 7. 기술 스택 
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


 
