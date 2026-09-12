import os
import time
from playwright.sync_api import sync_playwright

os.makedirs('DOCUMENTS/images', exist_ok=True)

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={'width': 1560, 'height': 980}, device_scale_factor=2)
    
    file_url = 'file:///' + os.path.abspath('web/index.html').replace('\\', '/')
    print('Navigating to:', file_url)
    page.goto(file_url)
    page.wait_for_timeout(3000)
    
    # 1. Full Terminal Overview
    page.screenshot(path='DOCUMENTS/images/terminal_overview.png', full_page=False)
    print('Captured terminal_overview.png')
    
    # 2. Toggle candlestick chart if available and capture chart & order entry area
    candlestick_btn = page.query_selector('button:has-text("CANDLES")') or page.query_selector('#chart-mode-candles')
    if candlestick_btn:
        candlestick_btn.click()
        page.wait_for_timeout(1000)
    page.screenshot(path='DOCUMENTS/images/candlestick_order_ticket.png', full_page=False)
    print('Captured candlestick_order_ticket.png')
    
    # 3. Open Academic Rubric & System Defense Inspector Modal
    rubric_btn = page.query_selector('#academic-rubric-btn') or page.query_selector('button:has-text("RUBRIC")')
    if rubric_btn:
        rubric_btn.click()
        page.wait_for_timeout(1000)
        page.screenshot(path='DOCUMENTS/images/academic_defense_modal.png', full_page=False)
        print('Captured academic_defense_modal.png')
        
        # Close modal if open
        close_btn = page.query_selector('#close-rubric-modal') or page.query_selector('button:has-text("CLOSE")')
        if close_btn:
            close_btn.click()
            page.wait_for_timeout(500)
            
    # 4. Open Hotkey matrix modal
    hotkey_btn = page.query_selector('#hotkey-help-btn') or page.query_selector('button:has-text("HOTKEYS")')
    if hotkey_btn:
        hotkey_btn.click()
        page.wait_for_timeout(1000)
        page.screenshot(path='DOCUMENTS/images/hotkey_matrix_modal.png', full_page=False)
        print('Captured hotkey_matrix_modal.png')

    browser.close()
print('Screenshots complete!')
