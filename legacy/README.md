# SMS Backup to Offline Viewer

Converts SMS Backup & Restore XML files into a self-contained, offline HTML viewer.

## Features

- **100% Offline** - No internet required, works completely locally
- **No Installation** - Just double-click to open in any browser
- **Easy Filtering** - Search by keyword, date range, contact, direction (sent/received)
- **Export Options** - Print to PDF (via browser) and Export to TXT
- **Mobile Friendly** - Responsive design works on phones and tablets
- **Fast** - Data is compressed and loaded efficiently

## Quick Start (Windows)

1. **Install Python** (if not already installed)
   - Download from: https://www.python.org/downloads/
   - During installation, CHECK the box "Add Python to PATH"

2. **Prepare your files**
   - Put these 3 files in the same folder:
     - `sms_processor.py`
     - `run_converter.bat`
     - Your XML backup file (rename it to `sms.xml`)

3. **Run the converter**
   - Double-click `run_converter.bat`
   - Wait for processing (may take 1-2 minutes for large files)
   - The viewer will open automatically when done

4. **Share with your client**
   - Give them the `sms_viewer_output` folder
   - They just need to double-click `SMS_Viewer.html`

## Manual Usage (Command Line)

```bash
python sms_processor.py your_backup.xml [output_folder]
```

Examples:
```bash
python sms_processor.py sms-20250620.xml
python sms_processor.py sms-20250620.xml ./client_messages
```

## Output Files

After processing, you'll get:

```
sms_viewer_output/
├── SMS_Viewer.html   ← Main viewer (give this to your client)
└── messages.json     ← Raw data for programmatic access
```

## Using the Viewer

### Filtering Messages

- **Search**: Type in the search box to find messages containing specific words
- **Date Range**: Set start and end dates to filter by time period
- **Direction**: Click "Received" or "Sent" to filter by message direction
- **Contact**: Click a contact in the sidebar or use the dropdown

### Exporting

- **Print to PDF**: Click "Print PDF" button → Choose "Save as PDF" in print dialog
- **Export TXT**: Click "Export TXT" → Downloads a text file with all filtered messages

## Troubleshooting

### "Python is not installed"
- Download Python from python.org
- Make sure to check "Add Python to PATH" during installation
- Restart your computer after installing

### "sms.xml not found"
- Rename your XML backup file to exactly `sms.xml`
- Make sure it's in the same folder as the scripts

### Viewer is slow or crashes
- This shouldn't happen with normal backups (even 100k+ messages)
- If it does, contact me and I can create a chunked/paginated version

### Messages look garbled
- The XML file might use a different encoding
- Try opening the XML in Notepad and re-saving as UTF-8

## Technical Details

- XML is parsed using Python's streaming iterparse (memory efficient)
- Data is compressed with gzip before embedding (~80% size reduction)
- Browser decompresses using native DecompressionStream API
- Pagination prevents rendering too many DOM elements at once
- Works in Chrome, Firefox, Edge, Safari (any modern browser)

## File Size Expectations

| XML Size | JSON Size | HTML Size | Load Time |
|----------|-----------|-----------|-----------|
| 10 MB    | ~2 MB     | ~1 MB     | < 1 sec   |
| 50 MB    | ~10 MB    | ~5 MB     | 1-2 sec   |
| 150 MB   | ~30 MB    | ~15 MB    | 3-5 sec   |
| 500 MB   | ~100 MB   | ~50 MB    | 10-15 sec |

---

Created for offline forensic/legal SMS review.
