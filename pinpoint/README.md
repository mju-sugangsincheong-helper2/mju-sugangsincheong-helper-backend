pinpoint 의 전체스택을 사용하지 않고 agent 기능만 사용해서 단순한 collector 를 개발하였다
이미 agent 는 bootRun 실행중에 자동으로 붙도록 설정되어 있다
uv run {project_root}/pinpoint/collector/mini-collector.py help 를 통해 실시간으로 agent 의 데이터를 수집하여 jsonl 형태로 저장되는 프로그램 또한 만들어 두었다

pinpoint 의 핵심 기능중에 하나인 call stack 분석 기능만 필요하여 분석 도구 또한 만들어 두었다
uv run {project_root}/pinpoint/analyzer/analyzer.py help 를 통해 분석 도구의 사용법을 확인할 수 있다