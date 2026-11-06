"""Test cases for the AI benchmark: invented lines, bills and voice clips with known right answers.

Every case is made up. Nothing here comes from a real ledger. TODAY is fixed so dates are checkable.
"""

TODAY = "2026-11-06"  # a Friday
FRIENDS = ["Rohan", "Ankit", "Priya", "Aaru", "Mayank"]

EXPENSE_CATEGORIES = ["Food", "Beverages", "Groceries", "Packaged goods", "Transport", "Shopping", "Self-care",
                      "Bills", "Entertainment", "Health", "Rent", "Investment", "Team Payment", "Other"]
INCOME_CATEGORIES = ["Income (Salary)", "Income (Client)", "Income (Other)"]
PAYMENT_METHODS = ["UPI", "PopUPI", "GPay", "PhonePe", "Paytm", "Debit Card", "Credit Card", "Cash", "Bank Transfer", "Other"]


def case(line, amount, merchant, category, payment="UPI", date="", split=(), type_="expense"):
    """category may be a tuple of acceptable answers; merchant is matched loosely (see score.py)."""
    return {
        "line": line, "amount": amount, "merchant": merchant,
        "category": category if isinstance(category, tuple) else (category,),
        "payment": payment, "date": date, "split": sorted(split), "type": type_,
    }


TEXT_CASES = [
    case("chai 40 cash", 40, "Chai", ("Beverages", "Food"), "Cash"),
    case("dinner 1200 with Rohan and Ankit", 1200, "Dinner", "Food", split=["Rohan", "Ankit"]),
    case("kal 5 sau ka petrol", 500, "Petrol", "Transport", date="2026-11-05"),
    case("swiggy 349 gpay", 349, "Swiggy", "Food", "GPay"),
    case("uber to office 236", 236, "Uber", "Transport"),
    case("1.2k groceries dmart card", 1200, "DMart", "Groceries", ("Debit Card", "Credit Card")),
    case("salary 85000 credited", 85000, "Salary", "Income (Salary)", ("Bank Transfer", "UPI"), type_="income"),
    case("movie tickets 2 for 640 with priya", 640, "Movie", "Entertainment", split=["Priya"]),
    case("rent 18000 bank transfer", 18000, "Rent", "Rent", "Bank Transfer"),
    case("dedh sau ka auto", 150, "Auto", "Transport"),
    case("paid 999 for netflix credit card", 999, "Netflix", ("Entertainment", "Bills"), "Credit Card"),
    case("medicines 420 apollo", 420, "Apollo", "Health"),
    case("electricity bill 2,340 paytm", 2340, "Electricity", "Bills", "Paytm"),
    case("haircut 300 cash yesterday", 300, "Haircut", "Self-care", "Cash", date="2026-11-05"),
    case("client paid 25000 for the logo", 25000, "Client", "Income (Client)", ("Bank Transfer", "UPI"), type_="income"),
    case("zomato 560 split with aaru", 560, "Zomato", "Food", split=["Aaru"]),
    case("2 coffees 380 starbucks", 380, "Starbucks", ("Beverages", "Food")),
    case("amazon order 1499", 1499, "Amazon", "Shopping"),
    case("goa trip hotel 9600 with rohan ankit priya", 9600, "Hotel", ("Other", "Rent", "Shopping"), split=["Rohan", "Ankit", "Priya"]),
    case("biscuits and maggi 120", 120, "Biscuits and Maggi", ("Packaged goods", "Groceries", "Food")),
    case("metro recharge 500 phonepe", 500, "Metro", "Transport", "PhonePe"),
    case("sip 5000 on 1 nov", 5000, "SIP", "Investment", ("Bank Transfer", "UPI"), date="2026-11-01"),
    case("refund from myntra 799", 799, "Myntra", "Income (Other)", type_="income"),
    case("parso pizza 650 with mayank", 650, "Pizza", "Food", date="2026-11-04", split=["Mayank"]),
    case("gym fees 1500 last friday", 1500, "Gym", ("Self-care", "Health"), date="2026-10-30"),
    case("bhai ko 2000 diye", 2000, "Bhai", ("Other", "Team Payment")),
    case("lunch 270 office canteen", 270, "Office canteen", "Food"),
    case("recharge jio 299", 299, "Jio", "Bills"),
    case("2 lakh gold", 200000, "Gold", ("Investment", "Shopping")),
    case("doodh 64 sabzi 90", 154, "Milk and vegetables", "Groceries"),
]

# Bills are drawn by make_assets.py with these fields; "expect" is what a good read returns.
BILLS = [
    {"id": "restaurant_gst", "kind": "restaurant", "merchant": "Punjab Grill", "date": "2026-11-03",
     "items": [("Dal Makhani", 420), ("Butter Naan x4", 240), ("Paneer Tikka", 380)], "cgst": 26.0, "sgst": 26.0,
     "discount": 0, "total": 1092, "payment": "Credit Card", "category": "Food"},
    {"id": "grocery_discount", "kind": "grocery", "merchant": "DMart", "date": "2026-11-02",
     "items": [("Atta 5kg", 245), ("Toor Dal 1kg", 168), ("Milk 1L x6", 384), ("Soap x4", 156)], "cgst": 0, "sgst": 0,
     "discount": 53, "total": 900, "payment": "UPI", "category": "Groceries"},
    {"id": "cafe_tip", "kind": "restaurant", "merchant": "Blue Tokai", "date": "2026-11-05",
     "items": [("Cappuccino", 240), ("Croissant", 190)], "cgst": 10.75, "sgst": 10.75, "tip": 40,
     "discount": 0, "total": 491.5, "payment": "Cash", "category": ("Beverages", "Food")},
    {"id": "upi_screenshot", "kind": "upi", "merchant": "Ramesh Kumar", "date": "2026-11-04",
     "items": [], "total": 1500, "payment": ("UPI", "GPay", "PhonePe"), "category": ("Other", "Team Payment")},
    {"id": "fuel", "kind": "fuel", "merchant": "Indian Oil", "date": "2026-11-01",
     "items": [("Petrol 5.12 L @ 97.65", 500)], "total": 500, "payment": "Cash", "category": "Transport"},
    {"id": "pharmacy", "kind": "grocery", "merchant": "Apollo Pharmacy", "date": "2026-10-28",
     "items": [("Paracetamol 650", 32), ("Vitamin D3", 289), ("Cough syrup", 118)], "cgst": 0, "sgst": 0,
     "discount": 22, "total": 417, "payment": "Debit Card", "category": "Health"},
    {"id": "restaurant_blurry", "kind": "restaurant", "merchant": "Barbeque Nation", "date": "2026-10-31",
     "items": [("Buffet Adult x3", 2397), ("Mocktail x2", 398)], "cgst": 69.88, "sgst": 69.88,
     "discount": 0, "total": 2934.76, "payment": "Credit Card", "category": "Food", "blur": True},
    {"id": "grocery_rotated", "kind": "grocery", "merchant": "Reliance Fresh", "date": "2026-11-06",
     "items": [("Bananas", 60), ("Tomatoes 1kg", 40), ("Bread", 50), ("Eggs x12", 84)], "cgst": 0, "sgst": 0,
     "discount": 0, "total": 234, "payment": "Cash", "category": "Groceries", "rotate": 4},
    {"id": "upi_received", "kind": "upi_in", "merchant": "Priya Sharma", "date": "2026-11-05",
     "items": [], "total": 750, "payment": ("UPI", "GPay", "PhonePe"), "category": "Income (Other)", "type": "income"},
    {"id": "electricity", "kind": "utility", "merchant": "Tata Power", "date": "2026-11-02",
     "items": [("Energy charges 214 units", 1712), ("Fixed charge", 110), ("Tax", 98)], "total": 1920,
     "payment": ("UPI", "Paytm", "Other", ""), "category": "Bills"},
]

# Voice clips: spoken by the laptop's English voice (make_assets.py), so Hinglish sounds foreign to the
# model. A rough proxy; the owner's own recordings are the real test.
VOICE_CASES = [
    case("Chai forty rupees cash", 40, "Chai", ("Beverages", "Food"), "Cash"),
    case("Dinner twelve hundred with Rohan and Ankit", 1200, "Dinner", "Food", split=["Rohan", "Ankit"]),
    case("Uber to office two hundred and thirty six", 236, "Uber", "Transport"),
    case("Swiggy three forty nine on G Pay", 349, "Swiggy", "Food", "GPay"),
    case("Petrol five hundred yesterday", 500, "Petrol", "Transport", date="2026-11-05"),
    case("Movie tickets six forty with Priya", 640, "Movie", "Entertainment", split=["Priya"]),
    case("Medicines four hundred and twenty at Apollo", 420, "Apollo", "Health"),
    case("Salary eighty five thousand credited", 85000, "Salary", "Income (Salary)", ("Bank Transfer", "UPI"), type_="income"),
]
