# Workbook-to-app mapping

Reference: `Personal_Finance_Master_Dashboard.xlsx`, explicitly confirmed by the owner
on 2026-09-08 because the linked Google Sheet was inaccessible to the connected account.
The original workbook is read-only and its personal values are not part of the APK.

| Workbook section | App destination |
| --- | --- |
| Dashboard | Home: Overview, Spending, Accounts, Actions; selected month and six-month history |
| Transactions | Activity: date, type, category, merchant, method, amount, essentials, account, notes; full detail and correction |
| EMI Tracker | Plan → Debts: lender, original/current balance, APR, payment, tenure/paid, dates, priority/status |
| Monthly Budget | Plan → Budget: planned income, essentials, discretionary, debt, savings versus actual |
| Cash Flow | Plan → Cash flow: 12 monthly estimates with opening/closing, allocations, net, runway |
| Goals | Plan → Goals: targets, savings, dates, monthly required amount, progress, gap, priority |
| Debt Planner | Plan → Debts: high-interest/small-balance ordering, extra payment, payoff months and interest estimate |
| Recommendations | Home → Actions: missing income/classification, savings, debt commitments, reserve and planning checks |
| Lists | Category choices and editable merchant rules; Plan → Settings for financial assumptions |
| Sheet1 / Expense Tracker | Settings → Workbook data preserves original populated rows without guessing their dates or duplicating ledger records |

## Accounting conventions

Confirmed records only, selected month/time zone and currency. Income and spending are
separate; spending includes EMI repayments and subtracts refunds. Own-account transfers
and ATM withdrawals are excluded by default, as are credit-card settlement records;
card purchases count when they occur. A user can override the payment purpose.
Gross category/merchant/method breakdowns are labelled before refunds. Unclassified
essential/discretionary spend is exposed rather than silently assumed essential.

Opening cash has an explicit opening month. Missing/ambiguous payment methods or cash
inputs yield an unavailable estimate. Forecast allocations are distinct from recorded
activity. Debt estimates use fixed rates and regular payments, excluding unentered fees.

## Import boundaries

Android's document picker grants access to the selected `.xlsx` only. The parser reads
bounded zipped XML, rejects entity declarations, handles shared/inline strings, both Excel
date systems, and percent-formatted APR. It never executes formulas or external links.
Normalized records require complete concrete inputs. All original populated cells remain
in a local snapshot; formula cells lacking saved results are displayed as formulas.
Reimport keeps existing plan records and uses stable transaction content/occurrence IDs;
editing financial facts in the source can create new records, and cross-source overlap
requires review. Erase local data removes both native records and the imported snapshot.

## Verification

Unit suites cover parser/capture, bank/merchant mappings, workbook fixtures and finance
calculations. Room's generated v2 schema retains every v1 table unchanged and matches all
six migration statements. Physical-device layout, accessibility and notification delivery
remain device acceptance checks; no guaranteed capture percentage is shown.

The actual reference file parsed successfully with all 11 tabs retained. No concrete
transaction, budget, debt or goal records were present in its supported input tables.
The original populated legacy/formula cells remain available in Workbook data after
the owner imports the file; they are not automatically treated as current finances.
