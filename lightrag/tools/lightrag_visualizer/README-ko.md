# LightRAG 3D Graph Viewer

RAG(Retrieval-Augmented Generation) 그래프 및 기타 그래프 구조를 시각화하고 분석하기 위해 LightRAG 패키지에 포함된 인터랙티브 3D 그래프 시각화 도구입니다.

![image](https://github.com/user-attachments/assets/b0d86184-99fc-468c-96ed-c611f14292bf)

## Installation

### Quick Install
```bash
pip install lightrag-hku[tools]  # Install with visualization tool only
# or
pip install lightrag-hku[api,tools]  # Install with both API and visualization tools
```

## Launch the Viewer
```bash
lightrag-viewer
```

## Features

- **3D Interactive Visualization**: ModernGL을 사용한 고성능 3D 그래픽 렌더링
- **Multiple Layout Algorithms**: 다양한 그래프 레이아웃 지원
  - Spring layout
  - Circular layout
  - Shell layout
  - Random layout
- **Community Detection**: 그래프 커뮤니티 구조의 자동 탐지 및 시각화
- **Interactive Controls**:
  - 카메라 이동을 위한 WASD + QE 키
  - 시야각 제어를 위한 마우스 오른쪽 버튼 드래그
  - 노드 선택 및 하이라이트
  - 노드 크기와 엣지 너비 조정 가능
  - 라벨 표시 설정 가능
  - 노드 연결 간 빠른 탐색

## Tech Stack

- **imgui_bundle**: 사용자 인터페이스
- **ModernGL**: OpenGL 그래픽 렌더링
- **NetworkX**: 그래프 데이터 구조 및 알고리즘
- **NumPy**: 수치 연산
- **community**: 커뮤니티 탐지

## Interactive Controls

### Camera Movement
- W: 앞으로 이동
- S: 뒤로 이동
- A: 왼쪽으로 이동
- D: 오른쪽으로 이동
- Q: 위로 이동
- E: 아래로 이동

### View Control
- 마우스 오른쪽 버튼을 누른 채 드래그하면 시점이 회전합니다

### Node Interaction
- 마우스를 올리면 노드가 하이라이트됩니다
- 클릭하면 노드를 선택합니다

## Visualization Settings

UI 제어판을 통해 조정 가능합니다:
- 레이아웃 유형
- 노드 크기
- 엣지 너비
- 라벨 표시 여부
- 라벨 크기
- 배경색

## Customization Options

- **Node Scaling**: `node_scale` 파라미터로 노드 크기 조정
- **Edge Width**: `edge_width` 파라미터로 엣지 너비 수정
- **Label Display**: `show_labels`로 라벨 표시 여부 전환
- **Label Size**: `label_size`로 라벨 크기 조정
- **Label Color**: `label_color`로 라벨 색상 설정
- **View Distance**: `label_culling_distance`로 라벨 표시 최대 거리 제어

## System Requirements

- Python 3.9+
- OpenGL 3.3+를 지원하는 그래픽 카드
- 지원 운영체제: Windows/Linux/MacOS

## Troubleshooting

### Common Issues

1. **Command Not Found**
   ```bash
   # Make sure you installed with the 'tools' option
   pip install lightrag-hku[tools]

   # Verify installation
   pip list | grep lightrag-hku
   ```

2. **ModernGL Initialization Failed**
   ```bash
   # Check OpenGL version
   glxinfo | grep "OpenGL version"

   # Update graphics drivers if needed
   ```

3. **Font Loading Issues**
   - 필요한 폰트는 패키지에 포함되어 있습니다
   - 문제가 계속되면 그래픽 드라이버를 확인하세요

## Usage with LightRAG

이 뷰어는 다음과 같은 작업에 특히 유용합니다:
- RAG 지식 그래프 시각화
- 문서 간 관계 분석
- 시맨틱 연결 탐색
- 검색(retrieval) 패턴 디버깅

## Performance Optimizations

- ModernGL을 사용한 효율적인 그래픽 렌더링
- 라벨 표시 최적화를 위한 시야 거리 컬링(view distance culling)
- 대규모 그래프의 시각화 최적화를 위한 커뮤니티 탐지 알고리즘

## Support

- GitHub Issues: [LightRAG Repository](https://github.com/HKUDS/LightRAG)
- Documentation: [LightRAG Docs](https://URL-to-docs)

## License

이 도구는 LightRAG의 일부이며 MIT License에 따라 배포됩니다. 자세한 내용은 `LICENSE`를 참조하세요.

참고: 이 시각화 도구는 LightRAG 패키지의 선택적 구성 요소입니다. 뷰어 기능을 사용하려면 [tools] 옵션과 함께 설치하세요.
