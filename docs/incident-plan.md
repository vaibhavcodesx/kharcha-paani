# Kharcha Paani: Personal Data Breach Incident Response Plan

> [!NOTE]
> **Disclaimer:** This document is an operational response plan drafted for Kharcha Paani under the
> Digital Personal Data Protection Act, 2023 (DPDP Act) and DPDP Rules, 2025. It is not legal advice.

---

## 1. Purpose and Scope

This incident response plan outlines the procedures to detect, contain, investigate, and report any
personal data breach involving Kharcha Paani.

Under Section 2(u) and Section 8(6) of the DPDP Act, 2023, and Rule 7 of the DPDP Rules, 2025, a
**personal data breach** is any unauthorized processing of personal data or accidental disclosure,
acquisition, sharing, alteration, destruction, or loss of access to personal data that compromises its
confidentiality, integrity, or availability.

### In Scope
- Cloud database and storage hosted on **Supabase** (Tokyo region, AWS `ap-northeast-1`): user profiles,
  expense/income transactions, split shares, IOUs, group memberships, and settlements.
- Authentication tokens and sessions handled via **Google Sign-In**.
- Transient AI payloads processed via the **Google Gemini API** (OCR bill photos, statement text).
- Administrative secrets and API keys (Supabase service role keys, Gemini API keys).

### Out of Scope
- Unencrypted data on an individual user's stolen or rooted physical device where no cloud infrastructure
  or fiduciary vulnerability was exploited (addressed by local Android sandbox and user device lock).

---

## 2. Roles and Contact Points

Kharcha Paani is maintained and operated by a solo data fiduciary. No formal Data Protection Officer
(DPO) is legally required as Kharcha Paani is not designated a Significant Data Fiduciary (SDF).

- **Data Fiduciary & Incident Response Lead:** Vaibhav Chauhan
- **Emergency Breach Contact Email:** `vaibhav.alt.chauhan@gmail.com`
- **Grievance Redressal Contact:** `vaibhav.alt.chauhan@gmail.com`
- **Supervisory Authority:** Data Protection Board of India (DPBI) (`https://dpbi.gov.in` / notified portal)
- **National Cybersecurity Agency:** Computer Emergency Response Team - India (CERT-In) (`incident@cert-in.org.in`)

---

## 3. Severity Classification

| Level | Severity | Description | Action Timeline |
|---|---|---|---|
| **P1** | **Critical** | Confirmed unauthorized extraction, deletion, or public exposure of cloud database records; leakage of Supabase service role keys or database credentials; mass account takeover. | Contain within 2 hours; Notify DPBI within 72 hours; Notify users without delay. |
| **P2** | **Major** | Flaw in Row Level Security (RLS) allowing cross-account or cross-group data leakage; unauthorized access to an isolated group's splits; transient exposure of API keys. | Contain within 6 hours; Assess DPBI reporting threshold within 24 hours. |
| **P3** | **Minor / Low** | Failed attack attempt; non-sensitive client bug; isolated transient error without data exposure or integrity loss. | Log internally; resolve in next regular release. No regulatory notification required. |

---

## 4. Four-Stage Incident Response Lifecycle

### Phase 1: Detection & Triage (Hours 0 – 4)
1. **Intake:** An incident is identified via automated monitoring alerts, Supabase auth/access anomaly logs,
   Google Cloud console alerts, bug bounty reports, or user grievance emails.
2. **Initial Assessment:** The Incident Lead verifies whether personal data was accessed, altered, or exfiltrated:
   - Identify which tables/records were touched (e.g. `users`, `transactions`, `splits`, `settlements`).
   - Determine the number of impacted Data Principals (users).
   - Assign initial severity (P1, P2, P3).

### Phase 2: Containment & Remediation (Hours 2 – 12)
1. **Immediate Containment:**
   - **Rotate Secrets:** Immediately regenerate compromised Supabase API keys (Anon key, Service Role key)
     and Gemini API keys via their respective provider dashboards.
   - **Revoke Sessions:** Terminate active sessions in Supabase Auth to force re-authentication with Google.
   - **Isolate Systems:** If a database vulnerability or RLS flaw is active, temporarily restrict write
     access or enable database maintenance mode while applying SQL hotfixes.
2. **Eradication:**
   - Deploy security hotfixes to the Supabase schema or Android app client.
   - Audit database access logs to verify that the attack vector is completely closed.

### Phase 3: Notification & Reporting (Hours 12 – 72)

Under DPDP Act Section 8(6) and Rule 7, notice must be sent **without delay** to affected users and the
Data Protection Board of India.

#### A. Notification to the Data Protection Board of India (DPBI)
- **Initial Notice:** Sent without delay once a breach is confirmed.
- **Comprehensive Final Report:** Must be submitted **within 72 hours** of becoming aware of the breach.
- **Reporting Channels:** Official DPBI portal (`https://dpbi.gov.in`) or notified electronic filing channel.
- **Required Details (DPDP Rule 7):**
  1. Description of the nature and circumstances of the breach.
  2. Timestamp of detection and estimated duration of breach.
  3. Categories and approximate number of Data Principals affected.
  4. Categories of personal data involved (e.g., names, email addresses, UPI IDs, expense transactions).
  5. Likely impact, consequences, and risks to Data Principals (e.g., financial privacy exposure).
  6. Mitigation, containment, and technical measures taken or planned.
  7. Contact details of the Data Fiduciary for ongoing communication.

#### B. Notification to Affected Users (Data Principals)
- **Timeline:** Communicated **without delay** following containment.
- **Channel:** Direct email to the registered Google account email address, accompanied by an in-app
  banner/modal notice upon next application open.
- **Required Details:**
  1. Plain-language explanation of what occurred and what data was involved.
  2. The potential consequences and risks to the user.
  3. Measures taken by Kharcha Paani to contain the incident and secure their account.
  4. Recommended actions the user should take (e.g., review UPI payment linkages, verify Google Account security).
  5. Grievance officer contact email (`vaibhav.alt.chauhan@gmail.com`) for questions and assistance.

#### C. CERT-In Notification (if applicable)
If the incident constitutes a cybersecurity incident under the CERT-In Directions of 2022 (e.g., unauthorized
access to cloud infrastructure or database ransomware), notify `incident@cert-in.org.in` within the applicable
statutory window (6 hours of notice/discovery).

### Phase 4: Post-Mortem & Documentation (Days 3 – 7)
1. **Incident Post-Mortem Report:** Document the root cause, timeline of events, vulnerabilities exploited,
   and preventative measures implemented.
2. **Audit Log Retention:** In accordance with DPDP Rule 8(3), retain detailed incident records, logs, and
   remediation evidence for **at least 1 year** from the date of the incident.
3. **Hardening:** Review and strengthen Supabase Row Level Security (RLS) policies, regression test suites,
   and dependency security audits to prevent recurrence.

---

## 5. Breach Notification Communication Templates

### Template 1: Notice to the Data Protection Board of India (DPBI)

```text
Subject: Notification of Personal Data Breach under Section 8(6) of DPDP Act, 2023 – Kharcha Paani

1. Data Fiduciary Details:
   - Entity: Kharcha Paani (Developer / Operator: Vaibhav Chauhan)
   - Contact Person: Vaibhav Chauhan (vaibhav.alt.chauhan@gmail.com)
   - Service: Native Android personal expense tracking and split ledger application

2. Incident Overview:
   - Date and time breach was detected: [YYYY-MM-DD HH:MM IST]
   - Estimated duration of unauthorized exposure: [Hours / Days]
   - Nature of incident: [e.g., Misconfiguration in cloud database access controls / Credential leak]

3. Affected Personal Data & Scale:
   - Approximate number of Data Principals affected: [Number]
   - Categories of data involved: [e.g., Names, email addresses, UPI IDs, expense split ledger rows]
   - Note on sensitive data: No passwords, credit/debit card numbers, or bank account credentials were
     stored or exposed (Google Sign-In handles authentication; UPI apps handle banking transfers).

4. Assessment of Impact:
   - Potential risks to Data Principals: [e.g., Privacy exposure of personal spending transactions and UPI IDs]

5. Containment & Remediation Actions Taken:
   - [e.g., Revoked compromised API keys and rotated database credentials on YYYY-MM-DD at HH:MM IST]
   - [e.g., Applied hotfix to database Row Level Security (RLS) policies]
   - [e.g., Invalidated active user sessions requiring Google Sign-In re-authentication]

6. User Communication:
   - Direct notification sent to all impacted Data Principals on [YYYY-MM-DD].

7. Ongoing Contact:
   - Vaibhav Chauhan, vaibhav.alt.chauhan@gmail.com
```

### Template 2: Notice to Affected Users (Data Principals)

```text
Subject: Important security notice regarding your Kharcha Paani account

Hi [Name],

We are writing to let you know about a security incident that involved some of your Kharcha Paani data,
what we have done to resolve it, and what you should know.

What happened?
On [Date], we discovered that [brief plain-language description: e.g., a vulnerability in our cloud database
allowed unauthorized access to certain records between [Date] and [Date]].

What data was involved?
The information involved may have included your profile name, email address, your UPI ID (if you added one),
and the expense items, group splits, and settlements you logged in the app.
Kharcha Paani never has access to your Google password, bank account login, or debit/credit card numbers.

What have we done?
As soon as we detected this issue, we took immediate steps to secure your data:
- We immediately closed the access vulnerability and updated our database security rules.
- We rotated all infrastructure credentials and terminated active application sessions.
- We notified the Data Protection Board of India in accordance with India's DPDP Act, 2023.

What should you do?
- You do not need to change any passwords for Kharcha Paani, as authentication is handled securely by Google.
- When you next open Kharcha Paani, you may be asked to sign in again with your Google account.
- If you had added a UPI ID, remain vigilant against any unsolicited payment requests or phishing attempts.

If you have any questions or concerns:
Please write directly to our grievance contact, Vaibhav Chauhan, at vaibhav.alt.chauhan@gmail.com. We take
your privacy seriously and deeply regret any concern this may cause.

Warmly,
Vaibhav Chauhan
Kharcha Paani
```

---

## 6. Review and Maintenance

This incident plan is reviewed annually or immediately following:
- Any amendment or official notification regarding the DPDP Rules or DPBI reporting guidelines.
- Any architectural change to backend infrastructure, cloud processors, or authentication providers.
- A post-incident review following any security event.
