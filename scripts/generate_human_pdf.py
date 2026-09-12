import os
import base64
from playwright.sync_api import sync_playwright

def get_image_base64(filepath):
    if os.path.exists(filepath):
        with open(filepath, "rb") as f:
            encoded = base64.b64encode(f.read()).decode("utf-8")
            ext = os.path.splitext(filepath)[1].lower().replace('.', '')
            mime = "image/png" if ext == "png" else "image/jpeg"
            return f"data:{mime};base64,{encoded}"
    return ""

print("Loading screenshots for Mansi Kumari's report...")
img_terminal = get_image_base64("DOCUMENTS/images/terminal_overview.png")
img_candlestick = get_image_base64("DOCUMENTS/images/candlestick_order_ticket.png")
img_rubric = get_image_base64("DOCUMENTS/images/academic_defense_modal.png")
img_hotkey = get_image_base64("DOCUMENTS/images/hotkey_matrix_modal.png")
img_tests = get_image_base64("DOCUMENTS/images/terminal_tests_run.png")

with open("scripts/report_template_human.html", "r", encoding="utf-8") as f:
    template = f.read()

# Replace image placeholders
html_final = template.replace("{{IMG_TERMINAL}}", img_terminal)
html_final = html_final.replace("{{IMG_CANDLESTICK}}", img_candlestick)
html_final = html_final.replace("{{IMG_RUBRIC}}", img_rubric)
html_final = html_final.replace("{{IMG_HOTKEY}}", img_hotkey)
html_final = html_final.replace("{{IMG_TESTS}}", img_tests)

temp_html = "DOCUMENTS/temp_mansi_report.html"
with open(temp_html, "w", encoding="utf-8") as f:
    f.write(html_final)

output_pdf = "DOCUMENTS/MarketPulse_Project_Report_MansiKumari_25BAI11449.pdf"

print(f"Generating PDF: {output_pdf} via Playwright Chromium...")

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page()
    file_path = os.path.abspath(temp_html).replace('\\', '/')
    page.goto(f"file:///{file_path}")
    page.wait_for_timeout(2500)
    
    page.pdf(
        path=output_pdf,
        format="A4",
        print_background=True,
        display_header_footer=True,
        header_template='<div style="font-size: 7.5pt; color: #94a3b8; width: 100%; text-align: right; padding-right: 16mm; font-family: sans-serif;">MarketPulse: Concurrency-Safe Matching Engine &middot; CSE2006</div>',
        footer_template='<div style="font-size: 7.5pt; color: #94a3b8; width: 100%; display: flex; justify-content: space-between; padding-left: 16mm; padding-right: 16mm; font-family: sans-serif;"><span>MANSI KUMARI (25BAI11449) &middot; B.Tech CSE (AI/ML) &middot; VIT Bhopal</span><span>Page <span class="pageNumber"></span> of <span class="totalPages"></span></span></div>',
        margin={
            "top": "16mm",
            "bottom": "18mm",
            "left": "16mm",
            "right": "16mm"
        }
    )
    browser.close()

if os.path.exists(temp_html):
    os.remove(temp_html)

file_size_kb = os.path.getsize(output_pdf) / 1024.0
print(f"SUCCESS: Created {output_pdf} ({file_size_kb:.1f} KB)")
