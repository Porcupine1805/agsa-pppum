# Hướng dẫn thực nghiệm tái lập từ build đến kết quả bài báo

Tài liệu này là quy trình chuẩn cho mã nguồn và tám bộ dữ liệu mới trong
`datasets/ready/`. Quy trình tạo ra CSV thô, bảng tổng hợp, bảng LaTeX, hình PDF/PNG,
log từng cấu hình và báo cáo kiểm tra tính đầy đủ. Không dùng các tệp `paper-results/`
hoặc `results/revision_*` làm kết quả công bố vì chúng thuộc phiên bản mã cũ.

## 1. Các profile và kết quả dự kiến

| Profile | Mục đích | Số cấu hình | Thuật toán/cấu hình | Số dòng CSV dự kiến |
|---|---|---:|---:|---:|
| `smoke` | kiểm tra nhanh toàn pipeline | 8 | 5 | 40 |
| `paper` | so sánh chính: 8 bộ dữ liệu × 3 `minPer` × 3 seed | 72 | 5 | 360 |
| `scaling` | 3 bộ lớn tại 10/25/50/100% số giao dịch | 12 | 5 | 60 |

Năm thuật toán trong ma trận công bố là `SMAU-PHUI`, `SMSE-PHUI`, `MU-MAP`, `AGSA`
và `MPH`. `maxLen=4` trong profile `paper`, `maxLen=3` trong profile `scaling`; đây
là một phần của định nghĩa thực nghiệm và phải ghi rõ trong bài báo.

## 2. Dữ liệu mới và ý nghĩa hai tệp của mỗi bộ

Tất cả dữ liệu chạy thực nghiệm nằm trong `datasets/ready/<dataset>/`:

- `item_count.txt`: mỗi dòng là một giao tác, mỗi phần tử có dạng `item:quantity`;
- `item_utility.txt`: mỗi dòng có dạng `item:profit`;
- `datasets/ready/manifest.csv`: số giao tác, số item, nguồn, ý nghĩa quantity/profit,
  phép chuyển đổi và SHA-256.

| Bộ dữ liệu | Giao tác | Item | Loại utility | Vai trò |
|---|---:|---:|---|---|
| Foodmart | 4.141 | 1.559 | thực | kiểm tra nhỏ |
| Liquor_11 | 9.283 | 2.626 | thực | bán lẻ thưa |
| ECommerce | 14.975 | 3.468 | thực | bán lẻ trực tuyến |
| Mushroom | 8.416 | 119 | tổng hợp | dữ liệu dày |
| Retail | 88.162 | 16.470 | tổng hợp | benchmark cỡ vừa |
| Fruithut | 181.970 | 1.263 | utility nội tại | mở rộng |
| Yoochoose-buy | 234.300 | 16.004 | utility nội tại | mở rộng |
| Chainstore | 1.112.949 | 46.086 | utility nội tại | benchmark lớn |

Với các nguồn SPMF chỉ cho utility nội tại, phép mã hóa đặt `profit=1` và
`quantity=internal utility`; do đó tổng utility dương được bảo toàn chính xác nhưng
quantity không được diễn giải là số đơn vị vật lý. Phải tách kết luận của nhóm utility
thực và nhóm utility tổng hợp/nội tại trong bài báo.

## 3. Yêu cầu phần mềm và tài nguyên

- JDK 17 trở lên: `java`, `javac`, `jar` có trong `PATH`;
- Python 3.10 trở lên và Git;
- tối thiểu 16 GiB RAM cho smoke; nên dùng 32–64 GiB cho `paper`/`scaling`;
- khoảng 1 GiB đĩa trống ngoài mã nguồn và dữ liệu.

Mở PowerShell và chạy:

```powershell
Set-Location <this-repository>
java -version
javac -version
python --version
git --version
```

## 4. Chuẩn bị dữ liệu từ đầu

Chỉ cần `git clone` khi chưa có `external/DSI-Lab1-PPUM`:

```powershell
git clone https://github.com/DSI-Lab1/PPUM.git external/DSI-Lab1-PPUM
python scripts/prepare_all_datasets.py --ppum-root external/DSI-Lab1-PPUM
python scripts/validate_dataset_layout.py
Get-Content datasets/ready/manifest.csv
```

`prepare_all_datasets.py` tải/chuẩn hóa nguồn, cộng các item trùng trong cùng giao tác,
kiểm tra kích thước và ghi hash. Chạy lại sẽ dùng lại tệp hợp lệ. Kết quả hợp lệ phải
có `datasets=8 errors=0` từ validator.

## 5. Build sạch và kiểm tra đúng đắn

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build.ps1
java -jar build/agsa-pppum.jar selftest
java -jar build/agsa-pppum.jar env --out results/run_environment.txt
python scripts/validate_dataset_layout.py
```

Không chạy ma trận công bố nếu `selftest` thất bại. Tiếp theo chạy 2.200 phản ví dụ có
chủ đích bằng oracle vét cạn và kiểm tra bất biến của sanitizer:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_adversarial_suite.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_adversarial_2000.ps1
Get-Content datasets/adversarial_200/summary.txt
Get-Content datasets/adversarial_2000/summary.txt
Import-Csv datasets/adversarial_200/failures.csv
Import-Csv datasets/adversarial_2000/failures.csv
```

Hai lệnh `Import-Csv` cuối không được trả về bản ghi nào; hai summary phải có
`failures=0`. Bộ nhỏ kiểm tra tính đúng đắn, không thay thế benchmark hiệu năng.

## 6. Chạy nhanh toàn pipeline trước khi chạy dài

Lệnh sau tự build, self-test, ghi môi trường, kiểm tra dữ liệu, sinh cấu hình, chạy,
kiểm tra CSV và vẽ hình:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_ready_pipeline.ps1 `
  -Profile smoke -BudgetGB 16 -SoloHeapGB 12
```

Xác minh đầu ra:

```powershell
python scripts/validate_results.py results/ready_smoke_runs.csv --allow-infeasible
Import-Csv results/ready_smoke_runs.csv | Group-Object status | Select-Object Name,Count
Get-ChildItem results/figures/smoke
```

Smoke đạt khi đủ 8 nhóm/40 dòng, không lỗi schema, mọi dòng khả thi có `HF=0`, và AGSA
hard guard có `AC=0`. `NO_PHUI`/`INFEASIBLE` là trạng thái khoa học cần báo cáo, không
được xóa khỏi CSV.

## 7. Chạy ma trận chính từ đầu đến cuối

Trước lần chạy dùng để công bố, khóa phiên bản mã và kiểm tra rằng Git trả về một
commit hash (không phải `UNAVAILABLE`):

```powershell
git status
git rev-parse HEAD
java -jar build/agsa-pppum.jar env --out results/run_environment.txt
```

Nếu dự án chưa có commit, hãy rà soát rồi tạo commit tái lập trước khi chạy; script
kiểm tra publication bundle sẽ không chấp nhận một môi trường thiếu commit hash.

Sinh lại cấu hình cố định trước khi chạy:

```powershell
python scripts/generate_ready_experiments.py --profile all
```

Máy 32–64 GiB:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_ready_pipeline.ps1 `
  -Profile paper -BudgetGB 28 -SoloHeapGB 20

powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_ready_pipeline.ps1 `
  -Profile scaling -BudgetGB 28 -SoloHeapGB 20 -SkipBuild
```

`BudgetGB` là tổng heap tối đa của các JVM chạy song song; `SoloHeapGB` là heap khi một
job lỗi được thử lại một mình. Với máy 16 GiB, dùng `-BudgetGB 10 -SoloHeapGB 10`; tốc
độ chậm hơn nhưng kết quả không đổi.

Các lệnh kiểm tra cuối:

```powershell
python scripts/validate_results.py results/ready_paper_runs.csv --allow-infeasible
python scripts/validate_results.py results/ready_scaling_runs.csv --allow-infeasible
python scripts/check_publication_bundle.py
Get-Content results/publication_bundle_check.md
```

Chỉ khi báo cáo ghi `Status: READY` mới coi gói thực nghiệm đã đủ cho bài báo.

## 8. Chạy tiếp, bỏ qua phần đã xong và chạy lại có chọn lọc

Runner lưu checkpoint độc lập trong `results/parts/ready_<profile>/`. Một checkpoint
chỉ được dùng lại khi có đúng `config_id` và đúng số dòng thuật toán.

### 8.1 Tiếp tục sau khi tắt máy hoặc job bị gián đoạn

Chạy lại đúng lệnh cũ, không dùng `-Fresh`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_ready_pipeline.ps1 `
  -Profile paper -BudgetGB 28 -SoloHeapGB 20 -SkipBuild
```

Log sẽ ghi `resume <configId>` cho checkpoint hợp lệ; chỉ cấu hình thiếu/hỏng được chạy.
Sau đó runner ghép lại toàn bộ `results/ready_paper_runs.csv`.

### 8.2 Chạy lại đúng một cấu hình nghi ngờ

Liệt kê `configId`:

```powershell
Import-Csv experiments/ready_paper.csv | Select-Object configId,dataset,minPer,seed
```

Ví dụ buộc chạy lại một cấu hình và giữ nguyên các checkpoint còn lại:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_ready_pipeline.ps1 `
  -Profile paper -BudgetGB 28 -SoloHeapGB 20 -SkipBuild `
  -RerunConfig 'paper-foodmart-n4141-p1-r1'
```

Nhiều cấu hình:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_ready_pipeline.ps1 `
  -Profile paper -BudgetGB 28 -SoloHeapGB 20 -SkipBuild `
  -RerunConfig 'paper-foodmart-n4141-p1-r1','paper-foodmart-n4141-p2-r1'
```

### 8.3 Bỏ toàn bộ checkpoint và chạy lại profile

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_ready_pipeline.ps1 `
  -Profile paper -BudgetGB 28 -SoloHeapGB 20 -Fresh
```

`-Fresh` ghi đè lần lượt các part của profile, không trộn kết quả cũ. Không kết hợp
`-Fresh` với `-RerunConfig`, và không chạy hai tiến trình cho cùng profile đồng thời.

### 8.4 Chỉ kiểm tra/vẽ lại, không chạy thuật toán

```powershell
python scripts/validate_results.py results/ready_paper_runs.csv --allow-infeasible
python scripts/plot_experiment_results.py results/ready_paper_runs.csv `
  --out results/figures/paper
python scripts/check_publication_bundle.py
python scripts/make_manuscript_figures.py
```

`plot_experiment_results.py` writes diagnostic plots under `results/figures/`.
`make_manuscript_figures.py` writes the submission figures (`Fig1.pdf`–`Fig3.pdf` and the ESM result PDFs) from the frozen CSVs in `results/paper-results/`.

## 9. Các kịch bản thực nghiệm được hỗ trợ

1. `smoke`: xác nhận cài đặt, parser, miner, sanitizer, CSV và plotting.
2. `paper`: so sánh chất lượng và thời gian giữa năm thuật toán trên tám bộ dữ liệu,
   ba mức periodicity và ba seed.
3. `scaling`: đánh giá tăng thời gian/bộ nhớ theo 10/25/50/100% dữ liệu lớn.
4. resume: cùng profile, không `-Fresh`; tái sử dụng part hoàn chỉnh.
5. selective rerun: `-RerunConfig <configId>`; thay đúng part rồi ghép lại toàn ma trận.
6. full rerun: `-Fresh`; dùng sau khi thay đổi mã, công thức hoặc định nghĩa metric.
7. validation-only/plot-only: dùng khi chỉ thay cách trình bày, không thay thuật toán.

Nếu thay ngưỡng, số seed, thuật toán hoặc `maxLen`, hãy tạo profile CSV có tên mới và
đầu ra mới, không ghi đè profile đã dùng trong bài. Có thể gọi runner trực tiếp:

```powershell
python scripts/run_publication_parallel.py `
  --config experiments/my_scenario.csv `
  --out results/my_scenario_runs.csv `
  --budget-gb 28 --solo-heap-gb 20
python scripts/validate_results.py results/my_scenario_runs.csv --allow-infeasible
python scripts/plot_experiment_results.py results/my_scenario_runs.csv `
  --out results/figures/my_scenario
```

## 10. Linux/WSL/macOS

Chạy sạch:

```bash
chmod +x scripts/build.sh scripts/run_ready_pipeline.sh
python3 -m pip install -r requirements-analysis.txt
BUDGET_GB=28 SOLO_HEAP_GB=20 ./scripts/run_ready_pipeline.sh paper
BUDGET_GB=28 SOLO_HEAP_GB=20 ./scripts/run_ready_pipeline.sh scaling
python3 scripts/check_publication_bundle.py
```

Chạy tiếp profile `paper` mà không build lại:

```bash
python3 scripts/run_publication_parallel.py \
  --config experiments/ready_paper.csv \
  --out results/ready_paper_runs.csv \
  --budget-gb 28 --solo-heap-gb 20
python3 scripts/validate_results.py results/ready_paper_runs.csv --allow-infeasible
python3 scripts/plot_experiment_results.py results/ready_paper_runs.csv \
  --out results/figures/paper
```

Thêm `--fresh` để chạy lại toàn bộ hoặc `--rerun-config CONFIG_ID` để chạy lại có chọn
lọc; hai tùy chọn loại trừ nhau.

## 11. Hồ sơ tối thiểu cần có để công bố

| Nhóm | Tệp | Nội dung bắt buộc |
|---|---|---|
| Nguồn gốc dữ liệu | `datasets/ready/manifest.csv` | nguồn, kích thước, semantics, chuyển đổi, SHA-256 |
| Môi trường | `results/run_environment.txt` | OS, JVM, CPU, RAM và thời điểm chạy |
| Cấu hình | `experiments/ready_paper.csv` | 72 cấu hình, seed, ngưỡng, `maxLen`, thuật toán |
| Cấu hình | `experiments/ready_scaling.csv` | 12 cấu hình và tỷ lệ dữ liệu |
| Đúng đắn | `datasets/adversarial_200/summary.txt`, `failures.csv` | số kiểm tra và không có failure |
| Đúng đắn | `datasets/adversarial_2000/summary.txt`, `failures.csv` | số kiểm tra và không có failure |
| Kết quả thô | `results/ready_paper_runs.csv` | 360 dòng ghép cặp; mọi metric và trạng thái |
| Kết quả thô | `results/ready_scaling_runs.csv` | 60 dòng scalability |
| Bảng tổng hợp | `results/figures/{paper,scaling}/summary.csv` | mean, SD, số lần lặp |
| Bảng bài báo | `results/figures/{paper,scaling}/summary_table.tex` | bảng LaTeX sinh tự động |
| Hình vector | `results/figures/{paper,scaling}/*.pdf` | runtime, loss, MC, AC, modified transactions |
| Hình xem nhanh | `results/figures/{paper,scaling}/*.png` | cùng hình ở 300 dpi |
| Audit | `results/publication_inventory.csv` | tồn tại, kích thước, SHA-256 từng tệp |
| Audit | `results/publication_bundle_check.md` | kết luận READY/NOT READY và lý do |

Nên nộp trong supplementary material:

- `results/parts/ready_{paper,scaling}/`: config, CSV và log từng job;
- `results/sensitive_sets/ready_{paper,scaling}/`: tập nhạy chính xác đã dùng;
- mã nguồn, `scripts/`, `RESULT_SCHEMA.md`, commit Git và hướng dẫn này;
- dữ liệu nếu giấy phép cho phép, hoặc manifest/hash và script tái tạo dữ liệu.

Không chỉ đưa trung bình vào bài. CSV thô phải giữ `status`, `message`, seed,
`sensitive_hash`, `HF`, `MC`, `AC`, utility loss, số ô/giao tác sửa, thời gian và peak
memory. Định nghĩa đầy đủ các cột nằm trong `RESULT_SCHEMA.md`.

## 12. Tệp/hình tương ứng với nội dung bài báo

- Bảng mô tả dữ liệu: lấy từ `datasets/ready/manifest.csv`.
- Bảng so sánh chính: `results/figures/paper/summary_table.tex`.
- Hình thời gian: `algorithmWall_ms.pdf`.
- Hình mất utility: `utilityLoss_pct.pdf`.
- Hình missing cost và artificial cost: `MC_pct.pdf`, `AC_pct.pdf`.
- Hình mức can thiệp: `modifiedTx_pct.pdf`.
- Phân tích scalability: cùng các tệp trong `results/figures/scaling/`.
- Chứng cứ đúng đắn: hai summary adversarial và `failures.csv` rỗng.
- Khả năng tái lập: environment, config CSV, manifest/hash, raw CSV và log.

Ví dụ nhúng vào LaTeX:

```latex
\input{../results/figures/paper/summary_table.tex}
\includegraphics[width=\linewidth]{../results/figures/paper/algorithmWall_ms.pdf}
```

## 13. Checklist khoa học trước khi khóa kết quả

1. Lưu commit mã nguồn trước khi chạy; không trộn kết quả trước/sau một thay đổi mã.
2. `validate_dataset_layout.py` báo 8 bộ và 0 lỗi.
3. Hai bộ adversarial có tổng 2.200 case và `failures=0`.
4. Mỗi cấu hình dùng cùng `sensitive_hash` cho mọi thuật toán.
5. Mọi dòng khả thi có `HF=0`; AGSA hard guard trên fixed timeline có `AC=0`.
6. Báo cáo cả `NO_PHUI`, `INFEASIBLE`, timeout và hết RAM, không loại âm thầm.
7. Bài báo nêu seed, số lặp, timeline, `maxLen`, mọi threshold và cách tạo tập nhạy.
8. Phân biệt quantity vật lý với utility nội tại được mã hóa thành quantity.
9. Hình dùng mean ± SD; bảng/hình phải sinh từ đúng CSV thô cùng lần chạy.
10. `python scripts/check_publication_bundle.py --strict` kết thúc với mã 0.

Nguồn dữ liệu được ghi chính xác trong `datasets/ready/manifest.csv`; không dùng tên
nguồn trên website thay cho citation và giấy phép chính thức trong bản thảo.
