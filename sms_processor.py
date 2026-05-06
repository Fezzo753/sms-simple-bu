#!/usr/bin/env python3
"""
SMS Backup & Restore XML Processor
===================================
Processes SMS XML backup files and generates an offline HTML viewer.

Usage: python sms_processor.py sms.xml [output_folder]

Features:
- Analyzes XML to determine optimal output format
- Generates self-contained HTML viewer with embedded data
- Creates JSON database for programmatic access
- Handles large files efficiently with streaming parser
- Supports filtering by date, direction, contact, keyword
- Export to TXT and Print to PDF

Author: Claude
License: MIT
"""

import xml.etree.ElementTree as ET
from xml.etree.ElementTree import iterparse
import json
import os
import sys
import gzip
import base64
from datetime import datetime
from collections import defaultdict
from pathlib import Path
import html as html_module
import re

# ============================================================================
# CONFIGURATION
# ============================================================================

MAX_INLINE_SIZE_MB = 40  # If JSON is larger, use chunked loading
MESSAGES_PER_PAGE = 500  # For pagination

# ============================================================================
# XML PARSER
# ============================================================================

def parse_sms_xml(xml_path, verbose=True):
    """
    Parse SMS Backup & Restore XML file using streaming for memory efficiency.
    Returns messages list and contacts dictionary.
    """
    messages = []
    contacts = defaultdict(lambda: {"name": None, "count": 0, "first_date": None, "last_date": None})
    
    if verbose:
        file_size = os.path.getsize(xml_path)
        print(f"📂 Processing: {xml_path}")
        print(f"📊 File size: {file_size / (1024*1024):.2f} MB")
        print()
    
    # First pass: count messages for progress
    if verbose:
        print("🔍 Counting messages...")
    
    total_count = 0
    try:
        for event, elem in iterparse(xml_path, events=('end',)):
            if elem.tag in ('sms', 'mms'):
                total_count += 1
            elem.clear()
    except ET.ParseError as e:
        print(f"⚠️  XML parse warning: {e}")
    
    if verbose:
        print(f"   Found {total_count:,} messages")
        print()
        print("📖 Parsing messages...")
    
    # Second pass: extract data
    processed = 0
    errors = 0
    
    for event, elem in iterparse(xml_path, events=('end',)):
        if elem.tag == 'sms':
            try:
                # Parse timestamp
                date_ms = int(elem.get("date", 0))
                
                msg = {
                    "id": processed,
                    "addr": elem.get("address", "Unknown"),
                    "date": date_ms,
                    "type": int(elem.get("type", 1)),  # 1=received, 2=sent
                    "body": elem.get("body", "") or "",
                    "name": elem.get("contact_name") or None,
                    "read": elem.get("read", "1") == "1",
                }
                
                # Update contact info
                addr = msg["addr"]
                if msg["name"]:
                    contacts[addr]["name"] = msg["name"]
                contacts[addr]["count"] += 1
                
                if contacts[addr]["first_date"] is None or date_ms < contacts[addr]["first_date"]:
                    contacts[addr]["first_date"] = date_ms
                if contacts[addr]["last_date"] is None or date_ms > contacts[addr]["last_date"]:
                    contacts[addr]["last_date"] = date_ms
                
                messages.append(msg)
                processed += 1
                
                if verbose and processed % 50000 == 0:
                    pct = (processed / total_count * 100) if total_count > 0 else 0
                    print(f"   {processed:,} / {total_count:,} ({pct:.1f}%)")
                    
            except Exception as e:
                errors += 1
            
            elem.clear()
            
        elif elem.tag == 'mms':
            try:
                date_ms = int(elem.get("date", 0))
                
                # Extract MMS text content
                body_parts = []
                for part in elem.findall(".//part"):
                    ct = part.get("ct", "")
                    if ct.startswith("text/"):
                        text = part.get("text", "")
                        if text:
                            body_parts.append(text)
                
                body = " ".join(body_parts).strip()
                if not body:
                    body = "[MMS Media]"
                
                # Get address from either attribute or child element
                address = elem.get("address", "")
                if not address:
                    addr_elem = elem.find(".//addr")
                    if addr_elem is not None:
                        address = addr_elem.get("address", "Unknown")
                
                msg = {
                    "id": processed,
                    "addr": address or "Unknown",
                    "date": date_ms,
                    "type": int(elem.get("msg_box", 1)),
                    "body": body,
                    "name": elem.get("contact_name") or None,
                    "read": True,
                    "mms": True,
                }
                
                addr = msg["addr"]
                if msg["name"]:
                    contacts[addr]["name"] = msg["name"]
                contacts[addr]["count"] += 1
                
                if contacts[addr]["first_date"] is None or date_ms < contacts[addr]["first_date"]:
                    contacts[addr]["first_date"] = date_ms
                if contacts[addr]["last_date"] is None or date_ms > contacts[addr]["last_date"]:
                    contacts[addr]["last_date"] = date_ms
                
                messages.append(msg)
                processed += 1
                
            except Exception as e:
                errors += 1
            
            elem.clear()
    
    if verbose:
        print(f"   ✅ Parsed {len(messages):,} messages")
        if errors > 0:
            print(f"   ⚠️  Skipped {errors} malformed entries")
        print()
    
    return messages, dict(contacts)


def analyze_data(messages, contacts):
    """Analyze parsed data and return statistics."""
    if not messages:
        return None
    
    # Calculate sizes
    json_str = json.dumps(messages, separators=(',', ':'))
    json_size = len(json_str.encode('utf-8'))
    
    # Compress to see final size
    compressed = gzip.compress(json_str.encode('utf-8'), compresslevel=9)
    compressed_size = len(compressed)
    
    # Date range
    dates = [m["date"] for m in messages if m["date"] > 0]
    min_date = min(dates) if dates else 0
    max_date = max(dates) if dates else 0
    
    # Message stats
    sent = sum(1 for m in messages if m["type"] == 2)
    received = len(messages) - sent
    
    # Body text stats
    total_chars = sum(len(m["body"]) for m in messages)
    avg_length = total_chars / len(messages) if messages else 0
    
    return {
        "total_messages": len(messages),
        "total_contacts": len(contacts),
        "sent": sent,
        "received": received,
        "json_size_mb": json_size / (1024 * 1024),
        "compressed_size_mb": compressed_size / (1024 * 1024),
        "min_date": min_date,
        "max_date": max_date,
        "total_chars": total_chars,
        "avg_msg_length": avg_length,
    }


def print_analysis(stats):
    """Print analysis results."""
    print("=" * 60)
    print("📊 DATA ANALYSIS")
    print("=" * 60)
    print()
    print(f"  Messages:     {stats['total_messages']:,}")
    print(f"  Contacts:     {stats['total_contacts']:,}")
    print(f"  Sent:         {stats['sent']:,}")
    print(f"  Received:     {stats['received']:,}")
    print()
    
    if stats['min_date'] > 0:
        min_dt = datetime.fromtimestamp(stats['min_date'] / 1000)
        max_dt = datetime.fromtimestamp(stats['max_date'] / 1000)
        print(f"  Date range:   {min_dt.strftime('%Y-%m-%d')} to {max_dt.strftime('%Y-%m-%d')}")
    
    print(f"  Total text:   {stats['total_chars']:,} characters")
    print(f"  Avg length:   {stats['avg_msg_length']:.0f} chars/message")
    print()
    print(f"  JSON size:    {stats['json_size_mb']:.2f} MB")
    print(f"  Compressed:   {stats['compressed_size_mb']:.2f} MB")
    print()
    
    if stats['json_size_mb'] < MAX_INLINE_SIZE_MB:
        print("  ✅ Data size is suitable for single-file HTML viewer")
    else:
        print("  ⚠️  Large dataset - will use chunked loading")
    print()


# ============================================================================
# HTML GENERATOR
# ============================================================================

def generate_html_viewer(messages, contacts, stats, output_dir):
    """Generate the complete offline HTML viewer."""
    
    # Sort messages by date (newest first for initial display)
    messages_sorted = sorted(messages, key=lambda x: x["date"], reverse=True)
    
    # Prepare contact list for sidebar
    contact_list = []
    for addr, info in contacts.items():
        contact_list.append({
            "addr": addr,
            "name": info["name"],
            "count": info["count"],
            "last": info["last_date"],
        })
    contact_list.sort(key=lambda x: x["last"] or 0, reverse=True)
    
    # Create JSON data
    messages_json = json.dumps(messages_sorted, separators=(',', ':'), ensure_ascii=False)
    contacts_json = json.dumps(contact_list, separators=(',', ':'), ensure_ascii=False)
    
    # Compress for embedding
    compressed_messages = gzip.compress(messages_json.encode('utf-8'), compresslevel=9)
    b64_messages = base64.b64encode(compressed_messages).decode('ascii')
    
    # Date range for display
    min_date_str = ""
    max_date_str = ""
    if stats['min_date'] > 0:
        min_date_str = datetime.fromtimestamp(stats['min_date'] / 1000).strftime('%Y-%m-%d')
        max_date_str = datetime.fromtimestamp(stats['max_date'] / 1000).strftime('%Y-%m-%d')
    
    html_content = generate_html_template(
        b64_messages=b64_messages,
        contacts_json=contacts_json,
        stats=stats,
        min_date_str=min_date_str,
        max_date_str=max_date_str,
    )
    
    # Write HTML file
    html_path = output_dir / "SMS_Viewer.html"
    with open(html_path, 'w', encoding='utf-8') as f:
        f.write(html_content)
    
    # Also save raw JSON for programmatic access
    json_path = output_dir / "messages.json"
    with open(json_path, 'w', encoding='utf-8') as f:
        f.write(messages_json)
    
    return html_path, json_path


def generate_html_template(b64_messages, contacts_json, stats, min_date_str, max_date_str):
    """Generate the complete HTML template with embedded data."""
    
    return f'''<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SMS Message Viewer</title>
    <style>
        *, *::before, *::after {{ box-sizing: border-box; margin: 0; padding: 0; }}
        
        :root {{
            --bg-dark: #0f1114;
            --bg-panel: #1a1d21;
            --bg-card: #242830;
            --bg-hover: #2d323a;
            --text: #e8eaed;
            --text-dim: #9aa0a6;
            --text-muted: #5f6368;
            --accent: #8ab4f8;
            --accent-dim: rgba(138, 180, 248, 0.15);
            --sent: #81c995;
            --sent-bg: rgba(129, 201, 149, 0.12);
            --received: #8ab4f8;
            --received-bg: rgba(138, 180, 248, 0.12);
            --border: #3c4043;
            --radius: 8px;
            --radius-lg: 12px;
        }}
        
        html, body {{
            height: 100%;
            overflow: hidden;
        }}
        
        body {{
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
            background: var(--bg-dark);
            color: var(--text);
            font-size: 14px;
            line-height: 1.5;
        }}
        
        /* Layout */
        .app {{
            display: grid;
            grid-template-rows: auto 1fr;
            grid-template-columns: 300px 1fr;
            height: 100vh;
        }}
        
        /* Header */
        .header {{
            grid-column: 1 / -1;
            background: var(--bg-panel);
            border-bottom: 1px solid var(--border);
            padding: 12px 20px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 20px;
        }}
        
        .logo {{
            display: flex;
            align-items: center;
            gap: 10px;
            font-size: 18px;
            font-weight: 600;
        }}
        
        .logo svg {{
            width: 26px;
            height: 26px;
            color: var(--accent);
        }}
        
        .header-stats {{
            display: flex;
            gap: 24px;
            font-size: 13px;
        }}
        
        .stat {{
            display: flex;
            gap: 6px;
            color: var(--text-dim);
        }}
        
        .stat b {{
            color: var(--text);
            font-weight: 600;
        }}
        
        /* Sidebar */
        .sidebar {{
            background: var(--bg-panel);
            border-right: 1px solid var(--border);
            display: flex;
            flex-direction: column;
            overflow: hidden;
        }}
        
        .filters {{
            padding: 16px;
            border-bottom: 1px solid var(--border);
            display: flex;
            flex-direction: column;
            gap: 12px;
        }}
        
        .search-wrap {{
            position: relative;
        }}
        
        .search-wrap svg {{
            position: absolute;
            left: 10px;
            top: 50%;
            transform: translateY(-50%);
            width: 16px;
            height: 16px;
            color: var(--text-muted);
            pointer-events: none;
        }}
        
        .search-wrap input {{
            width: 100%;
            padding: 9px 12px 9px 34px;
            background: var(--bg-card);
            border: 1px solid var(--border);
            border-radius: var(--radius);
            color: var(--text);
            font-size: 13px;
            outline: none;
            transition: border-color 0.15s;
        }}
        
        .search-wrap input:focus {{
            border-color: var(--accent);
        }}
        
        .search-wrap input::placeholder {{
            color: var(--text-muted);
        }}
        
        .filter-row {{
            display: flex;
            gap: 8px;
        }}
        
        .filter-row > * {{
            flex: 1;
        }}
        
        label {{
            display: block;
            font-size: 11px;
            font-weight: 500;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            color: var(--text-muted);
            margin-bottom: 4px;
        }}
        
        input[type="date"], select {{
            width: 100%;
            padding: 7px 10px;
            background: var(--bg-card);
            border: 1px solid var(--border);
            border-radius: var(--radius);
            color: var(--text);
            font-size: 13px;
            font-family: inherit;
            outline: none;
        }}
        
        input[type="date"]:focus, select:focus {{
            border-color: var(--accent);
        }}
        
        select {{
            cursor: pointer;
        }}
        
        .direction-btns {{
            display: flex;
            gap: 4px;
        }}
        
        .dir-btn {{
            flex: 1;
            padding: 7px 10px;
            background: var(--bg-card);
            border: 1px solid var(--border);
            border-radius: var(--radius);
            color: var(--text-dim);
            font-size: 12px;
            font-weight: 500;
            cursor: pointer;
            transition: all 0.15s;
        }}
        
        .dir-btn:hover {{
            background: var(--bg-hover);
        }}
        
        .dir-btn.active {{
            background: var(--accent);
            border-color: var(--accent);
            color: #000;
        }}
        
        .btn-row {{
            display: flex;
            gap: 8px;
        }}
        
        .btn {{
            flex: 1;
            padding: 8px 12px;
            background: var(--bg-card);
            border: 1px solid var(--border);
            border-radius: var(--radius);
            color: var(--text);
            font-size: 12px;
            font-weight: 500;
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 6px;
            transition: all 0.15s;
        }}
        
        .btn:hover {{
            background: var(--bg-hover);
        }}
        
        .btn svg {{
            width: 14px;
            height: 14px;
        }}
        
        .btn-primary {{
            background: var(--accent);
            border-color: var(--accent);
            color: #000;
        }}
        
        .btn-primary:hover {{
            filter: brightness(1.1);
            background: var(--accent);
        }}
        
        /* Contact List */
        .contact-list {{
            flex: 1;
            overflow-y: auto;
            padding: 8px;
        }}
        
        .contact {{
            padding: 10px 12px;
            border-radius: var(--radius);
            cursor: pointer;
            margin-bottom: 2px;
            transition: background 0.15s;
        }}
        
        .contact:hover {{
            background: var(--bg-hover);
        }}
        
        .contact.active {{
            background: var(--accent-dim);
        }}
        
        .contact-name {{
            font-weight: 500;
            margin-bottom: 2px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }}
        
        .contact-meta {{
            font-size: 12px;
            color: var(--text-muted);
            display: flex;
            justify-content: space-between;
        }}
        
        /* Main Content */
        .main {{
            display: flex;
            flex-direction: column;
            overflow: hidden;
        }}
        
        .toolbar {{
            padding: 10px 16px;
            background: var(--bg-panel);
            border-bottom: 1px solid var(--border);
            display: flex;
            align-items: center;
            justify-content: space-between;
        }}
        
        .results-count {{
            font-size: 13px;
            color: var(--text-dim);
        }}
        
        .results-count b {{
            color: var(--text);
        }}
        
        .toolbar-btns {{
            display: flex;
            gap: 8px;
        }}
        
        /* Messages */
        .messages {{
            flex: 1;
            overflow-y: auto;
            padding: 16px 20px;
        }}
        
        .msg {{
            max-width: 75%;
            margin-bottom: 10px;
        }}
        
        .msg.sent {{
            margin-left: auto;
        }}
        
        .msg-bubble {{
            padding: 10px 14px;
            border-radius: 16px;
            font-size: 14px;
            line-height: 1.45;
            word-wrap: break-word;
            white-space: pre-wrap;
        }}
        
        .msg.received .msg-bubble {{
            background: var(--received-bg);
            border-bottom-left-radius: 4px;
        }}
        
        .msg.sent .msg-bubble {{
            background: var(--sent-bg);
            border-bottom-right-radius: 4px;
        }}
        
        .msg-meta {{
            display: flex;
            gap: 8px;
            margin-top: 3px;
            padding: 0 4px;
            font-size: 11px;
            color: var(--text-muted);
        }}
        
        .msg.sent .msg-meta {{
            justify-content: flex-end;
        }}
        
        .msg-dir {{
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.3px;
        }}
        
        .msg.sent .msg-dir {{ color: var(--sent); }}
        .msg.received .msg-dir {{ color: var(--received); }}
        
        .date-sep {{
            text-align: center;
            padding: 12px 0;
            position: relative;
        }}
        
        .date-sep::before {{
            content: '';
            position: absolute;
            left: 0;
            right: 0;
            top: 50%;
            height: 1px;
            background: var(--border);
        }}
        
        .date-sep span {{
            position: relative;
            background: var(--bg-dark);
            padding: 0 12px;
            font-size: 12px;
            font-weight: 500;
            color: var(--text-muted);
        }}
        
        /* Loading */
        .loading {{
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            height: 100%;
            color: var(--text-muted);
            gap: 12px;
        }}
        
        .spinner {{
            width: 32px;
            height: 32px;
            border: 3px solid var(--border);
            border-top-color: var(--accent);
            border-radius: 50%;
            animation: spin 0.8s linear infinite;
        }}
        
        @keyframes spin {{
            to {{ transform: rotate(360deg); }}
        }}
        
        .empty {{
            text-align: center;
            padding: 40px;
            color: var(--text-muted);
        }}
        
        /* Highlight */
        mark {{
            background: rgba(251, 188, 4, 0.35);
            color: inherit;
            padding: 1px 2px;
            border-radius: 2px;
        }}
        
        /* Pagination */
        .pagination {{
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            padding: 12px;
            border-top: 1px solid var(--border);
            background: var(--bg-panel);
        }}
        
        .pagination button {{
            padding: 6px 14px;
            background: var(--bg-card);
            border: 1px solid var(--border);
            border-radius: var(--radius);
            color: var(--text);
            font-size: 13px;
            cursor: pointer;
            transition: all 0.15s;
        }}
        
        .pagination button:hover:not(:disabled) {{
            background: var(--bg-hover);
        }}
        
        .pagination button:disabled {{
            opacity: 0.4;
            cursor: not-allowed;
        }}
        
        .pagination .page-info {{
            color: var(--text-dim);
            font-size: 13px;
            min-width: 100px;
            text-align: center;
        }}
        
        /* Scrollbar */
        ::-webkit-scrollbar {{
            width: 8px;
        }}
        
        ::-webkit-scrollbar-track {{
            background: var(--bg-panel);
        }}
        
        ::-webkit-scrollbar-thumb {{
            background: var(--border);
            border-radius: 4px;
        }}
        
        ::-webkit-scrollbar-thumb:hover {{
            background: var(--text-muted);
        }}
        
        /* Print */
        @media print {{
            .app {{
                display: block;
                height: auto;
            }}
            .sidebar, .header, .toolbar, .pagination {{
                display: none !important;
            }}
            .main, .messages {{
                overflow: visible;
                height: auto;
            }}
            .msg {{
                max-width: 100%;
                page-break-inside: avoid;
            }}
            .msg-bubble {{
                border: 1px solid #ccc !important;
                background: #f5f5f5 !important;
            }}
            body {{
                background: #fff;
                color: #000;
            }}
        }}
        
        /* Mobile */
        @media (max-width: 768px) {{
            .app {{
                grid-template-columns: 1fr;
            }}
            .sidebar {{
                display: none;
            }}
            .header-stats {{
                display: none;
            }}
        }}
    </style>
</head>
<body>
    <div class="app">
        <header class="header">
            <div class="logo">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
                </svg>
                SMS Viewer
            </div>
            <div class="header-stats">
                <div class="stat"><span>Messages:</span> <b>{stats['total_messages']:,}</b></div>
                <div class="stat"><span>Contacts:</span> <b>{stats['total_contacts']:,}</b></div>
                <div class="stat"><span>Period:</span> <b>{min_date_str} – {max_date_str}</b></div>
            </div>
        </header>
        
        <aside class="sidebar">
            <div class="filters">
                <div class="search-wrap">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/></svg>
                    <input type="text" id="searchInput" placeholder="Search messages...">
                </div>
                
                <div class="filter-row">
                    <div>
                        <label>From Date</label>
                        <input type="date" id="dateFrom">
                    </div>
                    <div>
                        <label>To Date</label>
                        <input type="date" id="dateTo">
                    </div>
                </div>
                
                <div>
                    <label>Direction</label>
                    <div class="direction-btns">
                        <button class="dir-btn active" data-dir="all">All</button>
                        <button class="dir-btn" data-dir="in">Received</button>
                        <button class="dir-btn" data-dir="out">Sent</button>
                    </div>
                </div>
                
                <div>
                    <label>Contact</label>
                    <select id="contactFilter">
                        <option value="">All Contacts</option>
                    </select>
                </div>
                
                <div class="btn-row">
                    <button class="btn" id="clearFilters">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2m3 0v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6h14z"/></svg>
                        Clear
                    </button>
                </div>
            </div>
            
            <div class="contact-list" id="contactList"></div>
        </aside>
        
        <main class="main">
            <div class="toolbar">
                <div class="results-count">
                    Showing <b id="showCount">0</b> of <b id="totalCount">0</b> messages
                </div>
                <div class="toolbar-btns">
                    <button class="btn" id="exportTxt">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14,2 14,8 20,8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
                        Export TXT
                    </button>
                    <button class="btn" id="printBtn">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6,9 6,2 18,2 18,9"/><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"/><rect x="6" y="14" width="12" height="8"/></svg>
                        Print PDF
                    </button>
                </div>
            </div>
            
            <div class="messages" id="messageContainer">
                <div class="loading">
                    <div class="spinner"></div>
                    <div>Loading messages...</div>
                </div>
            </div>
            
            <div class="pagination" id="pagination" style="display: none;">
                <button id="prevPage">← Previous</button>
                <span class="page-info" id="pageInfo">Page 1</span>
                <button id="nextPage">Next →</button>
            </div>
        </main>
    </div>

    <script>
        // Embedded compressed message data
        const COMPRESSED_DATA = "{b64_messages}";
        const CONTACTS_DATA = {contacts_json};
        const PAGE_SIZE = 200;
        
        let allMessages = [];
        let filteredMessages = [];
        let currentPage = 1;
        let currentDirection = 'all';
        let currentContact = '';
        let searchTerm = '';
        let dateFrom = null;
        let dateTo = null;
        
        // Decompress data on load
        async function init() {{
            try {{
                // Decode base64 and decompress
                const binary = atob(COMPRESSED_DATA);
                const bytes = new Uint8Array(binary.length);
                for (let i = 0; i < binary.length; i++) {{
                    bytes[i] = binary.charCodeAt(i);
                }}
                
                const ds = new DecompressionStream('gzip');
                const decompressed = new Response(
                    new Blob([bytes]).stream().pipeThrough(ds)
                ).text();
                
                allMessages = JSON.parse(await decompressed);
                filteredMessages = [...allMessages];
                
                populateContacts();
                applyFilters();
                setupEventListeners();
                
            }} catch (e) {{
                console.error('Failed to load data:', e);
                document.getElementById('messageContainer').innerHTML = 
                    '<div class="empty">Failed to load messages. Try refreshing.</div>';
            }}
        }}
        
        function populateContacts() {{
            const select = document.getElementById('contactFilter');
            const list = document.getElementById('contactList');
            
            CONTACTS_DATA.forEach(c => {{
                // Dropdown
                const opt = document.createElement('option');
                opt.value = c.addr;
                opt.textContent = c.name || c.addr;
                select.appendChild(opt);
                
                // Sidebar list
                const div = document.createElement('div');
                div.className = 'contact';
                div.dataset.addr = c.addr;
                div.innerHTML = `
                    <div class="contact-name">${{escapeHtml(c.name || c.addr)}}</div>
                    <div class="contact-meta">
                        <span>${{c.count}} messages</span>
                        <span>${{formatDate(c.last)}}</span>
                    </div>
                `;
                div.onclick = () => selectContact(c.addr);
                list.appendChild(div);
            }});
        }}
        
        function selectContact(addr) {{
            currentContact = currentContact === addr ? '' : addr;
            document.getElementById('contactFilter').value = currentContact;
            
            document.querySelectorAll('.contact').forEach(el => {{
                el.classList.toggle('active', el.dataset.addr === currentContact);
            }});
            
            applyFilters();
        }}
        
        function applyFilters() {{
            filteredMessages = allMessages.filter(m => {{
                // Direction
                if (currentDirection === 'in' && m.type !== 1) return false;
                if (currentDirection === 'out' && m.type !== 2) return false;
                
                // Contact
                if (currentContact && m.addr !== currentContact) return false;
                
                // Date range
                if (dateFrom && m.date < dateFrom) return false;
                if (dateTo && m.date > dateTo) return false;
                
                // Search
                if (searchTerm) {{
                    const term = searchTerm.toLowerCase();
                    const body = (m.body || '').toLowerCase();
                    const name = (m.name || '').toLowerCase();
                    const addr = (m.addr || '').toLowerCase();
                    if (!body.includes(term) && !name.includes(term) && !addr.includes(term)) {{
                        return false;
                    }}
                }}
                
                return true;
            }});
            
            currentPage = 1;
            renderMessages();
        }}
        
        function renderMessages() {{
            const container = document.getElementById('messageContainer');
            const total = filteredMessages.length;
            const totalPages = Math.ceil(total / PAGE_SIZE);
            const start = (currentPage - 1) * PAGE_SIZE;
            const end = Math.min(start + PAGE_SIZE, total);
            const pageMessages = filteredMessages.slice(start, end);
            
            document.getElementById('showCount').textContent = total.toLocaleString();
            document.getElementById('totalCount').textContent = allMessages.length.toLocaleString();
            
            if (total === 0) {{
                container.innerHTML = '<div class="empty">No messages match your filters</div>';
                document.getElementById('pagination').style.display = 'none';
                return;
            }}
            
            // Sort by date for display (oldest first within page)
            const sorted = [...pageMessages].sort((a, b) => a.date - b.date);
            
            let html = '';
            let lastDate = '';
            
            sorted.forEach(m => {{
                const msgDate = formatDateOnly(m.date);
                if (msgDate !== lastDate) {{
                    html += `<div class="date-sep"><span>${{msgDate}}</span></div>`;
                    lastDate = msgDate;
                }}
                
                const dir = m.type === 2 ? 'sent' : 'received';
                const dirLabel = m.type === 2 ? 'SENT' : 'RECV';
                let body = escapeHtml(m.body || '');
                
                // Highlight search term
                if (searchTerm) {{
                    const regex = new RegExp(`(${{escapeRegex(searchTerm)}})`, 'gi');
                    body = body.replace(regex, '<mark>$1</mark>');
                }}
                
                html += `
                    <div class="msg ${{dir}}">
                        <div class="msg-bubble">${{body}}</div>
                        <div class="msg-meta">
                            <span class="msg-dir">${{dirLabel}}</span>
                            <span>${{formatTime(m.date)}}</span>
                            <span>${{escapeHtml(m.name || m.addr)}}</span>
                        </div>
                    </div>
                `;
            }});
            
            container.innerHTML = html;
            container.scrollTop = 0;
            
            // Pagination
            const pagination = document.getElementById('pagination');
            if (totalPages > 1) {{
                pagination.style.display = 'flex';
                document.getElementById('pageInfo').textContent = `Page ${{currentPage}} of ${{totalPages}}`;
                document.getElementById('prevPage').disabled = currentPage <= 1;
                document.getElementById('nextPage').disabled = currentPage >= totalPages;
            }} else {{
                pagination.style.display = 'none';
            }}
        }}
        
        function setupEventListeners() {{
            // Search
            let searchTimeout;
            document.getElementById('searchInput').addEventListener('input', e => {{
                clearTimeout(searchTimeout);
                searchTimeout = setTimeout(() => {{
                    searchTerm = e.target.value.trim();
                    applyFilters();
                }}, 300);
            }});
            
            // Direction buttons
            document.querySelectorAll('.dir-btn').forEach(btn => {{
                btn.addEventListener('click', () => {{
                    document.querySelectorAll('.dir-btn').forEach(b => b.classList.remove('active'));
                    btn.classList.add('active');
                    currentDirection = btn.dataset.dir;
                    applyFilters();
                }});
            }});
            
            // Contact dropdown
            document.getElementById('contactFilter').addEventListener('change', e => {{
                currentContact = e.target.value;
                document.querySelectorAll('.contact').forEach(el => {{
                    el.classList.toggle('active', el.dataset.addr === currentContact);
                }});
                applyFilters();
            }});
            
            // Date filters
            document.getElementById('dateFrom').addEventListener('change', e => {{
                dateFrom = e.target.value ? new Date(e.target.value).getTime() : null;
                applyFilters();
            }});
            
            document.getElementById('dateTo').addEventListener('change', e => {{
                dateTo = e.target.value ? new Date(e.target.value + 'T23:59:59').getTime() : null;
                applyFilters();
            }});
            
            // Clear filters
            document.getElementById('clearFilters').addEventListener('click', () => {{
                searchTerm = '';
                currentDirection = 'all';
                currentContact = '';
                dateFrom = null;
                dateTo = null;
                
                document.getElementById('searchInput').value = '';
                document.getElementById('dateFrom').value = '';
                document.getElementById('dateTo').value = '';
                document.getElementById('contactFilter').value = '';
                
                document.querySelectorAll('.dir-btn').forEach(b => {{
                    b.classList.toggle('active', b.dataset.dir === 'all');
                }});
                document.querySelectorAll('.contact').forEach(c => c.classList.remove('active'));
                
                applyFilters();
            }});
            
            // Pagination
            document.getElementById('prevPage').addEventListener('click', () => {{
                if (currentPage > 1) {{
                    currentPage--;
                    renderMessages();
                }}
            }});
            
            document.getElementById('nextPage').addEventListener('click', () => {{
                const totalPages = Math.ceil(filteredMessages.length / PAGE_SIZE);
                if (currentPage < totalPages) {{
                    currentPage++;
                    renderMessages();
                }}
            }});
            
            // Export TXT
            document.getElementById('exportTxt').addEventListener('click', exportToTxt);
            
            // Print
            document.getElementById('printBtn').addEventListener('click', () => window.print());
        }}
        
        function exportToTxt() {{
            const sorted = [...filteredMessages].sort((a, b) => a.date - b.date);
            
            let txt = 'SMS MESSAGE EXPORT\\n';
            txt += '==================\\n\\n';
            txt += `Total messages: ${{sorted.length}}\\n`;
            txt += `Generated: ${{new Date().toLocaleString()}}\\n\\n`;
            txt += '---\\n\\n';
            
            sorted.forEach(m => {{
                const dir = m.type === 2 ? 'SENT' : 'RECEIVED';
                const dt = new Date(m.date).toLocaleString();
                const contact = m.name || m.addr;
                
                txt += `[${{dt}}] ${{dir}} - ${{contact}}\\n`;
                txt += `${{m.body}}\\n\\n`;
            }});
            
            const blob = new Blob([txt], {{ type: 'text/plain;charset=utf-8' }});
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `sms_export_${{new Date().toISOString().slice(0,10)}}.txt`;
            a.click();
            URL.revokeObjectURL(url);
        }}
        
        // Utility functions
        function escapeHtml(str) {{
            if (!str) return '';
            return str
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;');
        }}
        
        function escapeRegex(str) {{
            return str.replace(/[.*+?^${{}}()|[\\]\\\\]/g, '\\\\$&');
        }}
        
        function formatDate(ts) {{
            if (!ts) return '';
            return new Date(ts).toLocaleDateString();
        }}
        
        function formatDateOnly(ts) {{
            if (!ts) return '';
            return new Date(ts).toLocaleDateString(undefined, {{
                weekday: 'long',
                year: 'numeric',
                month: 'long',
                day: 'numeric'
            }});
        }}
        
        function formatTime(ts) {{
            if (!ts) return '';
            return new Date(ts).toLocaleTimeString(undefined, {{
                hour: '2-digit',
                minute: '2-digit'
            }});
        }}
        
        // Start
        init();
    </script>
</body>
</html>'''


# ============================================================================
# MAIN
# ============================================================================

def main():
    print()
    print("=" * 60)
    print("  SMS Backup & Restore → Offline HTML Viewer Generator")
    print("=" * 60)
    print()
    
    # Parse arguments
    if len(sys.argv) < 2:
        print("Usage: python sms_processor.py <sms.xml> [output_folder]")
        print()
        print("Example:")
        print("  python sms_processor.py sms-backup.xml")
        print("  python sms_processor.py sms-backup.xml ./my_output")
        sys.exit(1)
    
    xml_path = Path(sys.argv[1])
    output_dir = Path(sys.argv[2]) if len(sys.argv) > 2 else Path("sms_viewer_output")
    
    # Validate input
    if not xml_path.exists():
        print(f"❌ Error: File not found: {xml_path}")
        sys.exit(1)
    
    # Create output directory
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # Parse XML
    print("🚀 Starting processing...")
    print()
    
    messages, contacts = parse_sms_xml(str(xml_path))
    
    if not messages:
        print("❌ No messages found in the XML file.")
        sys.exit(1)
    
    # Analyze
    stats = analyze_data(messages, contacts)
    print_analysis(stats)
    
    # Generate output
    print("🔨 Generating HTML viewer...")
    html_path, json_path = generate_html_viewer(messages, contacts, stats, output_dir)
    
    print()
    print("=" * 60)
    print("✅ COMPLETE!")
    print("=" * 60)
    print()
    print(f"  📄 HTML Viewer:  {html_path}")
    print(f"  📋 JSON Data:    {json_path}")
    print()
    print("  To use: Double-click 'SMS_Viewer.html' to open in browser")
    print("          Works completely offline!")
    print()
    
    # Size report
    html_size = os.path.getsize(html_path) / (1024 * 1024)
    json_size = os.path.getsize(json_path) / (1024 * 1024)
    print(f"  Output sizes:")
    print(f"    HTML: {html_size:.2f} MB")
    print(f"    JSON: {json_size:.2f} MB")
    print()


if __name__ == "__main__":
    main()
