#!/usr/bin/env python3
"""Render the synthetic PDF corpus.

Reads the Markdown sources in ``pdf-sources/`` and writes matching ``.pdf``
files into ``documents/``. PDFs are binary and should never be hand-edited --
edit the Markdown source and re-run this script instead.

    pip install fpdf2
    python sample-data/build-pdfs.py

Supported source syntax (deliberately a small subset):

    # Title              document title, first page only
    ## Heading           section heading
    ### Subheading       subsection heading
    KEY: value           header metadata block (before the first ##)
    - item               bullet
    <!-- PAGEBREAK -->   force a new page

Page breaks are explicit so that facts land on predictable pages -- the concept
document cites ``travel-expense-policy.pdf (p. 5)`` for the ground transport
rules, and that citation needs to stay true. Re-check it after editing that
source: content growing past a page boundary will shift everything after it.
"""

import sys
import unicodedata
from pathlib import Path

try:
    from fpdf import FPDF
except ImportError:  # pragma: no cover
    sys.exit("fpdf2 is required: pip install fpdf2")

ROOT = Path(__file__).resolve().parent
SRC_DIR = ROOT / "pdf-sources"
OUT_DIR = ROOT / "documents"

PAGEBREAK = "<!-- PAGEBREAK -->"

# The PDF core fonts are latin-1 only; keep the corpus plain-ASCII so nothing
# turns into a mojibake chunk after Tika extracts it.
REPLACEMENTS = {
    "‘": "'", "’": "'", "“": '"', "”": '"',
    "–": "-", "—": "-", "…": "...", " ": " ",
    "€": "EUR ",
}


def sanitize(text):
    for bad, good in REPLACEMENTS.items():
        text = text.replace(bad, good)
    return unicodedata.normalize("NFKD", text).encode("latin-1", "replace").decode("latin-1")


class PolicyPDF(FPDF):
    def __init__(self, filename):
        super().__init__(format="A4", unit="mm")
        self.doc_filename = filename
        self.set_margins(22, 20, 22)
        self.set_auto_page_break(auto=True, margin=22)

    def footer(self):
        self.set_y(-16)
        self.set_font("Helvetica", "I", 8)
        self.set_text_color(120)
        half = (self.w - self.l_margin - self.r_margin) / 2
        self.cell(half, 5, self.doc_filename, align="L")
        self.cell(half, 5, f"Page {self.page_no()}", align="R")
        self.set_text_color(0)


def write_rich(pdf, text, size, style="", height=5.5):
    """Write a wrapped run of text, honouring inline **bold** markers.

    ``write`` is used rather than ``multi_cell`` so the font can change
    mid-paragraph; it also wraps against the current left margin, which is how
    bullet indentation is done in ``render``.
    """
    for i, part in enumerate(text.split("**")):
        if not part:
            continue
        pdf.set_font("Helvetica", "B" if i % 2 else style, size)
        pdf.write(height, part)
    pdf.ln(height)


def render(pdf, lines):
    body_margin = pdf.l_margin

    for raw in lines:
        line = sanitize(raw.rstrip())
        pdf.set_left_margin(body_margin)
        pdf.set_x(body_margin)

        if line == PAGEBREAK:
            pdf.add_page()
        elif not line:
            pdf.ln(3)
        elif line.startswith("# "):
            write_rich(pdf, line[2:], 18, "B", height=9)
            pdf.ln(2)
        elif line.startswith("## "):
            pdf.ln(3)
            write_rich(pdf, line[3:], 13, "B", height=7)
            pdf.ln(1)
        elif line.startswith("### "):
            pdf.ln(2)
            write_rich(pdf, line[4:], 11, "B", height=6)
        elif line.startswith("- "):
            pdf.set_left_margin(body_margin + 6)
            pdf.set_x(body_margin)
            pdf.set_font("Helvetica", "", 10.5)
            pdf.write(5.5, chr(149) + "  ")
            write_rich(pdf, line[2:], 10.5)
        else:
            write_rich(pdf, line, 10.5)


def main():
    if not SRC_DIR.is_dir():
        sys.exit(f"missing source directory: {SRC_DIR}")
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    sources = sorted(SRC_DIR.glob("*.md"))
    if not sources:
        sys.exit(f"no .md sources found in {SRC_DIR}")

    for src in sources:
        out = OUT_DIR / (src.stem + ".pdf")
        pdf = PolicyPDF(out.name)
        pdf.add_page()
        render(pdf, src.read_text(encoding="utf-8").splitlines())
        pdf.output(str(out))
        print(f"{out.relative_to(ROOT.parent)}  ({pdf.page_no()} pages)")


if __name__ == "__main__":
    main()
