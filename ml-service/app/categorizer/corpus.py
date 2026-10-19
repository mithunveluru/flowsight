"""Seeded synthetic training corpus for the categorizer.

Merchants come from general knowledge of each market (India, US, UAE, Saudi Arabia) and the
descriptors follow the statement formats banks and card networks use there. The labeled
eval set under eval/categorization is never read here and was not used to write these
lists or templates. Labels follow the category rules in eval/README.md.

Every row carries a `group` (the merchant or template it came from) so validation can hold
out whole merchants.
"""

import random
import string

REGIONS = ("IN", "US", "AE", "SA")

CITIES = {
    "IN": ["MUMBAI", "BANGALORE", "BENGALURU", "NEW DELHI", "GURGAON", "NOIDA", "PUNE", "HYDERABAD", "CHENNAI",
           "KOLKATA", "AHMEDABAD", "JAIPUR", "KOCHI", "LUCKNOW", "INDORE", "CHANDIGARH", "COIMBATORE", "NAGPUR"],
    "US": ["NEW YORK", "BROOKLYN", "SAN FRANCISCO", "SEATTLE", "AUSTIN", "CHICAGO", "BOSTON", "DENVER", "MIAMI",
           "ATLANTA", "PHOENIX", "DALLAS", "SAN JOSE", "PORTLAND", "COLUMBUS", "CHARLOTTE", "SAN DIEGO", "HOUSTON"],
    "AE": ["DUBAI", "ABU DHABI", "SHARJAH", "AJMAN", "AL AIN", "RAS AL KHAIMAH", "FUJAIRAH", "DXB", "AUH"],
    "SA": ["RIYADH", "JEDDAH", "DAMMAM", "KHOBAR", "MAKKAH", "MADINAH", "TAIF", "ABHA", "TABUK", "BURAIDAH"],
}
US_STATES = {"NEW YORK": "NY", "BROOKLYN": "NY", "SAN FRANCISCO": "CA", "SEATTLE": "WA", "AUSTIN": "TX",
             "CHICAGO": "IL", "BOSTON": "MA", "DENVER": "CO", "MIAMI": "FL", "ATLANTA": "GA", "PHOENIX": "AZ",
             "DALLAS": "TX", "SAN JOSE": "CA", "PORTLAND": "OR", "COLUMBUS": "OH", "CHARLOTTE": "NC",
             "SAN DIEGO": "CA", "HOUSTON": "TX"}

FIRST_NAMES = {
    "IN": ["Rahul", "Priya", "Amit", "Sneha", "Vikram", "Anjali", "Suresh", "Kavya", "Arjun", "Deepa", "Rohit",
           "Pooja", "Manish", "Lakshmi", "Karthik", "Neha", "Sanjay", "Divya", "Abhishek", "Meera", "Venkatesh",
           "Ritu", "Harish", "Swati", "Gaurav", "Nandini"],
    "US": ["John", "Emily", "Michael", "Sarah", "David", "Jessica", "Chris", "Ashley", "Daniel", "Megan", "Kevin",
           "Laura", "Brian", "Rachel", "Jason", "Nicole", "Ryan", "Amanda", "Tyler", "Hannah"],
    "AE": ["Mohammed", "Ahmed", "Fatima", "Aisha", "Omar", "Khalid", "Mariam", "Rashid", "Sara", "Yousef", "Hamdan",
           "Noura", "Ravi", "Joseph", "Anil", "Maria", "Ali", "Layla"],
    "SA": ["Abdullah", "Mohammed", "Faisal", "Saud", "Nora", "Reem", "Turki", "Sultan", "Haya", "Fahad", "Abdulaziz",
           "Lama", "Nasser", "Majed", "Hessa", "Bandar", "Ali", "Ibrahim"],
}
LAST_NAMES = {
    "IN": ["Sharma", "Verma", "Patel", "Reddy", "Nair", "Iyer", "Gupta", "Singh", "Kumar", "Rao", "Menon", "Joshi",
           "Agarwal", "Chatterjee", "Pillai", "Desai", "Mehta", "Bhat", "Kulkarni", "Shetty", "Yadav", "Mishra"],
    "US": ["Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis", "Martinez", "Wilson",
           "Anderson", "Taylor", "Thomas", "Moore", "Jackson", "Lee", "Harris", "Clark"],
    "AE": ["Al Mansouri", "Al Hashimi", "Al Suwaidi", "Al Mazrouei", "Al Nuaimi", "Al Shamsi", "Al Kaabi", "Khan",
           "Thomas", "Fernandes", "Menon", "Al Falasi"],
    "SA": ["Al Otaibi", "Al Qahtani", "Al Ghamdi", "Al Harbi", "Al Shehri", "Al Dosari", "Al Mutairi", "Al Zahrani",
           "Al Anazi", "Al Shammari", "Al Subaie", "Al Malki"],
}
PEOPLE_AR = ["محمد العتيبي", "عبدالله القحطاني", "فهد الغامدي", "نورة الحربي", "سارة الشهري", "خالد الدوسري",
             "أحمد المطيري", "ريم الزهراني"]

EMPLOYERS = {
    "IN": ["Infosys Ltd", "Tata Consultancy Services", "Wipro Ltd", "HCL Technologies", "Tech Mahindra",
           "Accenture Solutions Pvt Ltd", "Cognizant Technology", "Larsen & Toubro", "Reliance Industries",
           "Flipkart Internet Pvt Ltd", "Zoho Corporation", "Mphasis Ltd", "Bosch Global Software", "Deloitte Shared"],
    "US": ["Acme Corp", "Northwind Traders", "Globex Inc", "Initech LLC", "Contoso Ltd", "Blue Harbor Health",
           "Summit Logistics", "Pinecrest Schools", "Redwood Analytics", "Brightline Media"],
    "AE": ["Al Futtaim Group", "Majid Al Futtaim", "Emaar Properties", "Dubai Holding", "Etihad Aviation",
           "Lulu Group", "Al Ghurair", "Dnata", "ADNOC Distribution", "Chalhoub Group"],
    "SA": ["Saudi Aramco", "SABIC", "STC", "Almarai", "Al Rajhi Bank", "Saudi Electricity", "Maaden",
           "Elm Company", "Jarir Marketing", "Savola Group"],
}

BANKS = {
    "IN": ["HDFC BANK", "ICICI BANK", "SBI", "AXIS BANK", "KOTAK MAHINDRA BANK", "YES BANK", "IDFC FIRST BANK",
           "INDUSIND BANK", "BANK OF BARODA", "PNB"],
    "US": ["CHASE", "CITI", "CAPITAL ONE", "DISCOVER", "AMEX", "WELLS FARGO", "BANK OF AMERICA", "SYNCHRONY",
           "US BANK", "BARCLAYCARD"],
    "AE": ["FAB", "MASHREQ", "DIB", "RAKBANK", "CBD", "HSBC UAE", "CITIBANK UAE", "ADIB", "EMIRATES ISLAMIC"],
    "SA": ["AL RAJHI BANK", "SNB", "RIYAD BANK", "SAB", "ALINMA BANK", "BANQUE SAUDI FRANSI", "BANK ALBILAD",
           "ARAB NATIONAL BANK"],
}
IFSC_PREFIX = ["HDFC", "ICIC", "SBIN", "UTIB", "KKBK", "YESB", "IDFB", "INDB", "BARB", "PUNB"]
UPI_HANDLES = ["ybl", "okaxis", "oksbi", "okhdfcbank", "paytm", "ibl", "axl", "upi", "icici"]

# category -> region -> brand names; "kind" picks the descriptor family (see TEMPLATES)
BRANDS = {
    "FOOD_DINING": {
        "IN": ["Swiggy", "Zomato", "Domino's Pizza", "McDonald's", "KFC", "Pizza Hut", "Burger King", "Starbucks",
               "Cafe Coffee Day", "Haldiram's", "Barbeque Nation", "Chaayos", "Third Wave Coffee", "Subway",
               "Behrouz Biryani", "Faasos", "Wow Momo", "Paradise Biryani", "Saravana Bhavan", "Social",
               "Mainland China", "Theobroma", "Blue Tokai", "Truffles", "Meghana Foods", "EatSure", "Box8",
               "Chai Point", "Bikanervala", "A2B Adyar Ananda Bhavan"],
        "US": ["Starbucks", "McDonald's", "Chipotle", "Chick-fil-A", "Taco Bell", "Wendy's", "Dunkin'",
               "Panera Bread", "DoorDash", "Uber Eats", "Grubhub", "Domino's", "Subway", "Shake Shack",
               "Five Guys", "Sweetgreen", "Panda Express", "Olive Garden", "Applebee's", "In-N-Out Burger",
               "Popeyes", "Jersey Mike's", "Cava", "Raising Cane's", "Peet's Coffee", "Dutch Bros", "Postmates",
               "Denny's", "IHOP", "Buffalo Wild Wings"],
        "AE": ["Talabat", "Deliveroo", "Noon Food", "Tim Hortons", "Starbucks", "Shake Shack", "Operation Falafel",
               "Allo Beirut", "McDonald's", "KFC", "Pizza Hut", "Hardee's", "Jollibee", "Costa Coffee",
               "Salt", "Al Mallah", "Zaatar w Zeit", "Ravi Restaurant", "Logma", "Arabian Tea House",
               "Paul Bakery", "Five Guys", "Texas Roadhouse", "Al Safadi", "Bait Al Mandi", "Common Grounds",
               "Wingstop", "Pret A Manger", "Calicut Paragon", "Gazebo"],
        "SA": ["Al Baik", "Kudu", "HungerStation", "Jahez", "Mrsool", "Herfy", "Barn's", "Dr.Cafe", "Starbucks",
               "McDonald's", "Shawarmer", "Maestro Pizza", "Dunkin", "Half Million", "Al Romansiah", "Najd Village",
               "Section-B", "Burgerizzr", "Kyan", "ToYou", "Tim Hortons", "Al Tazaj", "Mama Noura", "Hamburgini",
               "Overdose Coffee", "Joe & The Juice", "Shake Shack", "Baskin Robbins", "Krispy Kreme", "Lafah"],
    },
    "GROCERIES": {
        "IN": ["BigBasket", "Blinkit", "Zepto", "Swiggy Instamart", "DMart", "Reliance Fresh", "Reliance Smart",
               "More Supermarket", "Spencer's", "Nature's Basket", "JioMart", "Ratnadeep", "Star Bazaar",
               "Nilgiris", "Vijetha Supermarket", "Heritage Fresh", "Easyday", "Spar Hypermarket", "Lulu Hypermarket",
               "Country Delight", "Milkbasket", "Licious", "FreshToHome", "Big Bazaar"],
        "US": ["Kroger", "Safeway", "Whole Foods Market", "Trader Joe's", "Publix", "Aldi", "H-E-B", "Wegmans",
               "Albertsons", "Food Lion", "Instacart", "Sprouts Farmers Market", "Giant Eagle", "Stop & Shop",
               "Meijer", "Harris Teeter", "WinCo Foods", "Hy-Vee", "Shoprite", "Fresh Market", "Ralphs",
               "Vons", "Jewel-Osco", "Lidl", "Fairway Market"],
        "AE": ["Carrefour", "Lulu Hypermarket", "Spinneys", "Waitrose", "Choithrams", "Union Coop", "Viva Supermarket",
               "Kibsons", "Talabat Mart", "Al Maya Supermarket", "Grandiose", "West Zone", "Nesto Hypermarket",
               "Abu Dhabi Coop", "Sharjah Coop", "Geant", "Almadina Hypermarket", "Instashop", "Day To Day",
               "Kenz Hypermarket"],
        "SA": ["Panda", "Tamimi Markets", "Danube", "Othaim Markets", "Lulu Hypermarket", "Carrefour", "Nana",
               "Farm Superstores", "BinDawood", "Hyper Panda", "Manuel Market", "Al Raya", "Nesto", "Ninja",
               "Saudi Hypermarket", "Al Sadhan", "Grand Hyper", "Marhaba Markets"],
    },
    "SHOPPING": {
        "IN": ["Amazon", "Flipkart", "Myntra", "Ajio", "Meesho", "Nykaa", "Tata CLiQ", "Croma", "Reliance Digital",
               "Decathlon", "Westside", "Shoppers Stop", "Pantaloons", "Max Fashion", "IKEA", "FirstCry",
               "Vijay Sales", "Bata", "Tanishq", "Lenskart", "Pepperfry", "Urban Ladder", "Snapdeal", "Zudio",
               "Trends", "FabIndia", "Raymond", "Puma", "Titan"],
        "US": ["Amazon", "Target", "Best Buy", "eBay", "Etsy", "The Home Depot", "Lowe's", "Macy's", "Nordstrom",
               "TJ Maxx", "Kohl's", "Apple Store", "Nike", "Old Navy", "Wayfair", "Marshalls", "Ross Stores",
               "Gap", "Sephora", "Dick's Sporting Goods", "IKEA", "Bed Bath", "Michaels", "Temu", "Shein",
               "Zara", "Uniqlo", "REI", "Ace Hardware", "Staples"],
        "AE": ["Noon", "Amazon.ae", "Namshi", "Centrepoint", "Sharaf DG", "IKEA", "H&M", "Zara", "Max", "Home Centre",
               "Virgin Megastore", "Jumbo Electronics", "Emax", "Dubai Duty Free", "Ounass", "Splash", "Ace",
               "Dragon Mart", "Brands For Less", "Sun & Sand Sports", "Apple Store", "Daiso", "Pan Emirates",
               "Damas Jewellery", "Shein"],
        "SA": ["Noon", "Amazon.sa", "Jarir Bookstore", "Extra", "Centrepoint", "Namshi", "Shein", "Saco",
               "Nice One", "IKEA", "H&M", "Zara", "Max", "Home Centre", "Abyat", "Al Hokair Fashion", "Redtag",
               "Ounass", "Sun & Sand Sports", "Apple Store", "Virgin Megastore", "Lulu Fashion", "Golden Scent",
               "Arabian Oud", "Al Shaya"],
    },
    "TRANSPORTATION": {
        "IN": ["Uber", "Ola", "Rapido", "Namma Yatri", "Indian Oil", "HPCL", "BPCL", "Shell", "Nayara Energy",
               "Paytm FASTag", "ICICI FASTag", "Delhi Metro DMRC", "BMRCL Namma Metro", "Mumbai Metro One",
               "BluSmart", "Yulu", "Park+", "Jio-bp", "Bounce", "Hyderabad Metro"],
        "US": ["Uber", "Lyft", "Shell", "Chevron", "Exxon", "Mobil", "BP", "Speedway", "Wawa", "Sunoco",
               "Circle K", "Valero", "Marathon", "76 Gas", "Costco Gas", "E-ZPass", "SunPass", "FasTrak",
               "ParkMobile", "SpotHero", "NJ Transit", "BART", "Ventra CTA", "SEPTA", "Clipper Card", "Lime",
               "Citi Bike", "Amtrak Commuter", "Sheetz", "QuikTrip"],
        "AE": ["Careem", "Uber", "ENOC", "ADNOC", "Emarat", "Salik", "RTA Nol", "Mawaqif", "Darb", "Hala Taxi",
               "Dubai Taxi", "Arabia Taxi", "Udrive", "Ekar", "Parkin", "Yango", "Bolt", "Abu Dhabi Taxi",
               "RTA Parking", "Petrol Station"],
        "SA": ["Careem", "Uber", "Jeeny", "Aldrees", "SASCO", "Petromin", "Naft", "Riyadh Metro", "Bolt",
               "Kaiian", "SAPTCO City Bus", "Mawaqif Parking", "Sahel Fuel", "Alnajah Fuel", "Gasoline Station",
               "Riyadh Bus", "Mohtaref Parking", "Ejar Car Rental", "Yelo Car Rental", "Theeb Rent A Car"],
    },
    "UTILITIES": {
        "IN": ["BESCOM", "Tata Power", "Adani Electricity", "MSEDCL", "TANGEDCO", "BSES Rajdhani", "BSES Yamuna",
               "Mahanagar Gas", "Indraprastha Gas", "Indane Gas", "HP Gas", "Bharat Gas", "Airtel", "Jio",
               "Vi Vodafone Idea", "BSNL", "ACT Fibernet", "Hathway", "Tata Play", "Dish TV", "BWSSB",
               "Delhi Jal Board", "Torrent Power", "KSEB", "TSSPDCL", "Excitel", "Airtel Xstream Fiber"],
        "US": ["Con Edison", "PG&E", "Duke Energy", "Xfinity", "Comcast", "Verizon", "AT&T", "T-Mobile",
               "Spectrum", "National Grid", "Dominion Energy", "Georgia Power", "Southern California Edison",
               "Xcel Energy", "Cox Communications", "Optimum", "Frontier", "Mint Mobile", "Waste Management",
               "Republic Services", "Peoples Gas", "Austin Energy", "Seattle City Light", "Google Fiber",
               "Visible Wireless"],
        "AE": ["DEWA", "ADDC", "AADC", "SEWA", "FEWA", "Etisalat", "e& UAE", "du", "Empower", "Tabreed",
               "Emirates Gas", "Virgin Mobile UAE", "Lootah Gas", "OSN", "Etihad Water & Electricity"],
        "SA": ["Saudi Electricity Company", "SEC", "National Water Company", "STC", "Mobily", "Zain KSA",
               "Virgin Mobile KSA", "Lebara KSA", "Salam Mobile", "Marafiq", "Go Telecom", "Shahid VIP Box",
               "Dawiyat", "Integrated Telecom"],
    },
    "ENTERTAINMENT": {
        "IN": ["Netflix", "Disney+ Hotstar", "Prime Video", "Spotify", "YouTube Premium", "JioCinema", "SonyLIV",
               "Zee5", "Gaana", "JioSaavn", "PVR INOX", "Cinepolis", "BookMyShow", "Paytm Insider", "Wonderla",
               "Smaaash", "Timezone", "Steam", "PlayStation Store", "Xbox", "Apple Music", "Aha", "MX Player",
               "Imagicaa", "Dream11", "Kiddopia", "Audible"],
        "US": ["Netflix", "Hulu", "Disney+", "HBO Max", "Peacock", "Paramount+", "Spotify", "Apple Music",
               "YouTube Premium", "AMC Theatres", "Regal Cinemas", "Cinemark", "Fandango", "Ticketmaster",
               "StubHub", "Eventbrite", "Steam", "PlayStation Network", "Xbox Live", "Nintendo eShop",
               "Six Flags", "Dave & Buster's", "Topgolf", "Pandora", "SiriusXM", "Audible", "Twitch",
               "Live Nation", "Bowlero", "Alamo Drafthouse"],
        "AE": ["Netflix", "Shahid", "OSN+", "Anghami", "StarzPlay", "Spotify", "VOX Cinemas", "Reel Cinemas",
               "Novo Cinemas", "Roxy Cinemas", "Platinumlist", "Dubai Parks and Resorts", "IMG Worlds",
               "Ferrari World", "Warner Bros World", "Aquaventure", "Dubai Frame", "Burj Khalifa At The Top",
               "Global Village", "Steam", "PlayStation Store", "Yas Waterworld", "Magic Planet", "Ski Dubai"],
        "SA": ["Netflix", "Shahid", "Anghami", "StarzPlay", "Spotify", "VOX Cinemas", "Muvi Cinemas",
               "AMC Cinemas", "Empire Cinemas", "Webook", "Boulevard Riyadh City", "Riyadh Season", "Sparky's",
               "Snow City", "Al Hokair Land", "Steam", "PlayStation Store", "Xbox", "Jeddah Season",
               "Boulevard World", "Winter Wonderland Riyadh", "OSN+", "Shahid VIP", "Hala Yalla"],
    },
    "HEALTHCARE": {
        "IN": ["Apollo Pharmacy", "MedPlus", "Netmeds", "PharmEasy", "Tata 1mg", "Practo", "Dr Lal PathLabs",
               "Thyrocare", "Metropolis Healthcare", "Max Healthcare", "Fortis Hospital", "Manipal Hospitals",
               "Narayana Health", "Wellness Forever", "Aster CMI", "Cloudnine Hospital", "Healthians",
               "Truemeds", "Medanta", "Kauvery Hospital", "Sankara Nethralaya", "Clove Dental", "Vasan Eye Care",
               "Frank Ross Pharmacy", "Guardian Pharmacy"],
        "US": ["CVS Pharmacy", "Walgreens", "Rite Aid", "Quest Diagnostics", "LabCorp", "Kaiser Permanente",
               "One Medical", "MinuteClinic", "Zocdoc", "GoodRx", "Teladoc", "Aspen Dental", "LensCrafters",
               "Mayo Clinic", "Cleveland Clinic", "Duane Reade", "Express Scripts", "Capsule Pharmacy",
               "Planned Parenthood", "Urgent Care", "Hims & Hers", "Warby Parker", "Banner Health"],
        "AE": ["Aster Pharmacy", "Life Pharmacy", "Boots", "BinSina Pharmacy", "Aster Clinic", "Mediclinic",
               "NMC Healthcare", "Thumbay Hospital", "Medcare Hospital", "Cleveland Clinic Abu Dhabi",
               "King's College Hospital Dubai", "Prime Hospital", "Supercare Pharmacy", "Al Manara Pharmacy",
               "Emirates Hospital", "Burjeel Hospital", "Seha", "Unilabs", "Magrabi Optical", "Health Hub"],
        "SA": ["Nahdi", "Al Dawaa Pharmacy", "Dr. Sulaiman Al Habib", "Mouwasat Hospital", "Dallah Hospital",
               "Kingdom Hospital", "Whites Pharmacy", "United Pharmacy", "Al Mouwasat Pharmacy", "Saudi German Hospital",
               "Dr. Samir Abbas Hospital", "Al Borg Diagnostics", "Magrabi Hospital", "Care Medical",
               "Seha Virtual", "Sehhaty", "Al Hammadi Hospital", "Tadawi Pharmacy"],
    },
    "EDUCATION": {
        "IN": ["Byju's", "Unacademy", "Vedantu", "Physics Wallah", "upGrad", "Coursera", "Udemy", "Simplilearn",
               "Great Learning", "Allen Career Institute", "Aakash Institute", "FIITJEE", "Delhi Public School",
               "Kendriya Vidyalaya", "Amity University", "Manipal Academy", "Sapna Book House", "Toppr",
               "Scaler Academy", "Testbook", "Made Easy", "Narayana Group", "Sri Chaitanya", "Christ University"],
        "US": ["Coursera", "Udemy", "edX", "Chegg", "Pearson", "McGraw Hill", "College Board", "Kaplan",
               "Princeton Review", "Barnes & Noble College", "Rosetta Stone", "Khan Academy", "MasterClass",
               "Skillshare", "Codecademy", "Brilliant", "Sylvan Learning", "Kumon", "Varsity Tutors",
               "Arizona State University", "New York University", "University of Texas", "Ohio State Bursar",
               "Community College Tuition"],
        "AE": ["GEMS Education", "Taaleem", "Aldar Academies", "Dubai College", "Amity University Dubai",
               "Middlesex University Dubai", "University of Wollongong Dubai", "Kings' School", "Coursera",
               "Udemy", "Berlitz", "British Council", "Emirates Driving Institute", "Belhasa Driving Centre",
               "Kumon UAE", "Repton School", "Nord Anglia", "Magrudy's Books"],
        "SA": ["Noon Academy", "King Saud University", "Coursera", "Udemy", "Riyadh Schools", "Kingdom Schools",
               "Al Faisaliah Schools", "Manarat Schools", "British Council KSA", "Saudi Driving School",
               "Dar Al Uloom University", "Effat University", "Prince Sultan University", "Alfaisal University",
               "Madrasati", "Jarir Books", "American International School Riyadh"],
    },
    "TRAVEL": {
        "IN": ["MakeMyTrip", "Goibibo", "Cleartrip", "Yatra", "ixigo", "IRCTC", "IndiGo", "Air India", "SpiceJet",
               "Akasa Air", "Vistara", "OYO Rooms", "Taj Hotels", "ITC Hotels", "Treebo", "redBus", "Airbnb",
               "EaseMyTrip", "Booking.com", "Agoda", "FabHotels", "Lemon Tree Hotels", "Thomas Cook India",
               "Zostel", "AbhiBus", "Air India Express", "Club Mahindra"],
        "US": ["Delta Air Lines", "United Airlines", "American Airlines", "Southwest Airlines", "JetBlue",
               "Alaska Airlines", "Spirit Airlines", "Frontier Airlines", "Expedia", "Booking.com", "Hotels.com",
               "Marriott", "Hilton", "Hyatt", "Holiday Inn", "Airbnb", "Vrbo", "Amtrak", "Greyhound", "Kayak",
               "Priceline", "Hertz", "Enterprise Rent-A-Car", "Avis", "Best Western", "Carnival Cruise",
               "TSA PreCheck", "Sheraton"],
        "AE": ["Emirates Airline", "Etihad Airways", "flydubai", "Air Arabia", "Wego", "Rotana Hotels",
               "Jumeirah Hotels", "Atlantis The Palm", "Booking.com", "Agoda", "Expedia", "Airbnb", "Marriott",
               "Hilton", "Rove Hotels", "Musafir", "Dnata Travel", "Holiday Factory", "Address Hotels",
               "Wizz Air Abu Dhabi", "Hertz UAE", "Tripadvisor"],
        "SA": ["Saudia", "flynas", "flyadeal", "Almosafer", "Flyin", "Saudi Arabia Railways", "Haramain High Speed Railway",
               "Booking.com", "Agoda", "Marriott", "Hilton", "Holiday Inn", "Movenpick Hotels", "Rotana",
               "Gathern", "Tajawal", "Wego", "Riyadh Air", "Elaf Hotels", "Swiss International Hotels",
               "Budget Rent A Car", "Hertz KSA"],
    },
    "SUBSCRIPTIONS": {
        "*": ["Adobe", "Microsoft 365", "Google One", "Google Workspace", "iCloud Storage", "Dropbox", "Notion",
              "Canva", "OpenAI ChatGPT", "GitHub", "AWS", "Google Cloud", "DigitalOcean", "Zoom", "Slack",
              "Grammarly", "LinkedIn Premium", "1Password", "NordVPN", "Evernote", "Figma", "Medium", "Substack",
              "Bloomberg", "The Economist", "Duolingo Plus", "Headspace", "Calm", "Strava", "Todoist",
              "JetBrains", "Atlassian", "Vercel", "Heroku", "Cloudflare", "Mailchimp", "Shopify", "Squarespace",
              "Wix", "GoDaddy", "Namecheap", "Microsoft Azure", "Netlify", "Linear", "Loom", "Miro"],
        "IN": ["The Hindu Subscription", "Times Prime", "ET Prime", "Hindustan Times Premium", "Gold's Gym India",
               "Anytime Fitness", "Fitpass", "HealthifyMe", "Snap Fitness", "Talwalkars", "Moneycontrol Pro",
               "Indian Express Premium", "Zoho Mail", "Tally Solutions"],
        "US": ["NYTimes", "Wall Street Journal", "Washington Post", "The Atlantic", "Planet Fitness", "LA Fitness",
               "Equinox", "Crunch Fitness", "24 Hour Fitness", "Peloton", "Orangetheory", "Gold's Gym",
               "Anytime Fitness", "ClassPass", "YMCA Membership", "Life Time Fitness", "Intuit QuickBooks",
               "Norton", "McAfee", "Costco Membership", "Sam's Club Membership", "Amazon Prime Membership"],
        "AE": ["Gulf News Plus", "Khaleej Times Digital", "Fitness First", "GymNation", "Gold's Gym UAE",
               "Anytime Fitness UAE", "Warehouse Gym", "Fit Republik", "Barry's Dubai", "Talabat Pro",
               "Careem Plus", "Noon One", "Amazon Prime UAE"],
        "SA": ["Fitness Time", "Leejam Sports", "Body Masters", "Gold's Gym KSA", "Armah Sports", "Fitness Time Pro",
               "Noon VIP", "Amazon Prime KSA", "Asharq Al-Awsat Digital", "Arab News Subscription", "Fitness Time Ladies",
               "Wellness Club Membership"],
    },
    "FINANCE": {
        "IN": ["LIC", "HDFC Life", "ICICI Prudential Life", "SBI Life", "Star Health", "Niva Bupa", "Max Life Insurance",
               "Tata AIG", "Acko", "Digit Insurance", "Policybazaar", "Zerodha", "Groww", "Upstox", "Angel One",
               "Kuvera", "NPS Trust", "Bajaj Finserv", "Tata Capital", "HDB Financial", "Home Credit", "Muthoot Finance",
               "LIC Housing Finance", "CRED", "Simpl", "LazyPay", "Slice", "KreditBee", "Navi", "Paytm Money",
               "HDFC Mutual Fund", "SBI Mutual Fund", "Mirae Asset", "Coin by Zerodha"],
        "US": ["GEICO", "State Farm", "Progressive", "Allstate", "Liberty Mutual", "Lemonade", "Nationwide", "MetLife",
               "Robinhood", "Vanguard", "Fidelity", "Charles Schwab", "E*TRADE", "Coinbase", "Betterment",
               "Wealthfront", "Affirm", "Klarna", "Afterpay", "Sezzle", "Sallie Mae", "Navient", "SoFi", "Rocket Mortgage",
               "Ally Auto", "Toyota Financial", "Honda Financial", "Mr. Cooper", "LendingClub", "Upstart"],
        "AE": ["Tabby", "Tamara", "Postpay", "Cashew", "Sarwa", "StashAway", "Interactive Brokers", "Baraka",
               "GIG Gulf Insurance", "Sukoon Insurance", "Orient Insurance", "Daman Health Insurance", "Takaful Emarat",
               "AXA Insurance", "Liva Insurance", "Policybazaar UAE", "Emirates Investment", "Wio Invest",
               "eToro", "Al Hilal Takaful"],
        "SA": ["Tabby", "Tamara", "Tawuniya", "Bupa Arabia", "Medgulf", "Al Rajhi Takaful", "Walaa Insurance",
               "Derayah", "Al Rajhi Capital", "Aljazira Capital", "Sahm Capital", "Tadawul", "Abyan Capital",
               "Emkan Finance", "Nayifat Finance", "Tamweel Aloula", "Lendo", "Sukuk Platform", "SNB Capital",
               "Malath Insurance"],
    },
}

# generic nouns that make a local business recognisable without a brand
GENERIC = {
    "FOOD_DINING": {"*": ["Restaurant", "Cafe", "Bakery", "Grill", "Kitchen", "Pizzeria", "Coffee House", "Bistro",
                          "Juice Bar", "Sweets", "Burger House", "Food Court"],
                    "IN": ["Biryani House", "Dhaba", "Bhojanalaya", "Tiffin Centre", "Chaat Corner", "Udupi Hotel",
                           "Mess", "Canteen", "Darshini", "Tea Stall"],
                    "US": ["Diner", "BBQ", "Taqueria", "Deli & Grill", "Bagels", "Sushi Bar", "Steakhouse", "Pub"],
                    "AE": ["Cafeteria", "Shawarma", "Karak", "Mandi", "Restaurant LLC", "Baqala Cafe"],
                    "SA": ["Mandi", "Shawarma", "Bukhari", "Kabsa", "Broasted", "Cafe & Roastery"],
                    "ar": ["مطعم", "مقهى", "مخبز", "شاورما", "مطابخ"]},
    "GROCERIES": {"*": ["Supermarket", "Grocery", "Hypermarket", "Mini Mart", "Fresh Market", "Food Mart",
                        "Vegetables & Fruits", "Organic Store"],
                  "IN": ["Kirana Store", "Provision Store", "General Stores", "Departmental Store", "Dairy",
                         "Fruits & Vegetables", "Super Bazaar"],
                  "US": ["Farmers Market", "Grocery Outlet", "Market & Deli", "Food Co-op"],
                  "AE": ["Baqala", "Grocery LLC", "Foodstuff Trading", "Supermarket LLC"],
                  "SA": ["Baqala", "Tamween", "Food Stores"],
                  "ar": ["سوبرماركت", "بقالة", "أسواق", "تموينات"]},
    "SHOPPING": {"*": ["Fashion", "Electronics", "Footwear", "Mobiles", "Garments", "Gift Shop", "Toys",
                       "Furniture", "Home Decor", "Boutique", "Watches", "Jewellers", "Perfumes", "Optical Store"],
                 "IN": ["Textiles", "Saree Centre", "Readymades", "Mobile World", "Computers"],
                 "US": ["Outlet", "Thrift Store", "Hardware", "Appliances"],
                 "AE": ["Trading LLC Electronics", "Textiles LLC", "Perfumes LLC", "Mobile Phones LLC"],
                 "SA": ["Perfumes", "Abayas", "Oud", "Mobile Center"],
                 "ar": ["عطور", "ملابس", "أجهزة", "معرض"]},
    "TRANSPORTATION": {"*": ["Fuel Station", "Petrol Station", "Service Station", "Parking", "Taxi", "Car Wash"],
                       "IN": ["Petrol Pump", "Filling Station", "Auto Fuels", "Cab Services", "Toll Plaza"],
                       "US": ["Gas Station", "Gas & Go", "Parking Garage", "Toll Road", "Transit"],
                       "AE": ["Petrol Station LLC", "Parking Fees", "Car Park"],
                       "SA": ["Fuel Station", "Petrol Station Co", "Parking"],
                       "ar": ["محطة وقود", "محطة بنزين", "مواقف"]},
    "UTILITIES": {"*": ["Broadband", "Electricity Bill", "Water Bill", "Gas Bill", "Internet Services",
                        "Cable TV"],
                  "IN": ["Electricity Board", "Gas Agency", "Fiber Net", "Water Supply"],
                  "US": ["Electric Coop", "Water Utility", "Power & Light", "Sanitation"],
                  "AE": ["District Cooling", "Gas Services"],
                  "SA": ["Water Services"],
                  "ar": ["فاتورة كهرباء", "فاتورة مياه"]},
    "ENTERTAINMENT": {"*": ["Cinemas", "Bowling", "Arcade", "Gaming Zone", "Amusement Park", "Theatre",
                            "Concert Tickets", "Escape Room", "Trampoline Park"],
                      "IN": ["Fun World", "Multiplex", "Water Park"],
                      "US": ["Comedy Club", "Mini Golf", "Laser Tag"],
                      "AE": ["Entertainment LLC", "Theme Park"],
                      "SA": ["Entertainment Center", "Fun City"],
                      "ar": ["سينما", "ملاهي"]},
    "HEALTHCARE": {"*": ["Pharmacy", "Clinic", "Hospital", "Dental Clinic", "Diagnostics", "Medical Centre",
                         "Eye Care", "Physiotherapy", "Pathology Lab", "Polyclinic"],
                   "IN": ["Medical Store", "Medicals", "Chemist & Druggist", "Nursing Home", "Ayurveda Clinic"],
                   "US": ["Urgent Care", "Pediatrics", "Family Medicine", "Dermatology"],
                   "AE": ["Pharmacy LLC", "Medical Center LLC"],
                   "SA": ["Pharmacy", "Medical Complex"],
                   "ar": ["صيدلية", "مستشفى", "عيادة", "مجمع طبي"]},
    "EDUCATION": {"*": ["School", "Academy", "Institute", "College", "University", "Tuition Fees", "Book Store",
                        "Learning Centre", "Coaching Classes"],
                  "IN": ["Public School", "Vidyalaya", "Tutorials", "Classes", "Convent School"],
                  "US": ["Tutoring", "Montessori", "Charter School", "Bursar"],
                  "AE": ["International School", "Training Institute"],
                  "SA": ["International School", "Training Center"],
                  "ar": ["مدارس", "معهد", "جامعة"]},
    "TRAVEL": {"*": ["Travels", "Tours & Travels", "Hotel", "Resort", "Airlines", "Holidays", "Guest House",
                     "Inn & Suites"],
               "IN": ["Residency", "Tour Operators", "Homestay", "Bus Travels"],
               "US": ["Motel", "Lodge", "Airport Hotel"],
               "AE": ["Hotel Apartments", "Tourism LLC", "Desert Safari"],
               "SA": ["Hotel Suites", "Umrah Services", "Furnished Apartments"],
               "ar": ["فندق", "سياحة وسفر", "شقق مفروشة"]},
    "SUBSCRIPTIONS": {"*": ["Gym", "Fitness Club", "Fitness Center", "Software", "Cloud Hosting", "Membership",
                            "Health Club", "Digital Subscription"],
                      "IN": ["Gym & Fitness", "Yoga Studio"],
                      "US": ["Athletic Club", "CrossFit"],
                      "AE": ["Fitness LLC"],
                      "SA": ["Fitness"],
                      "ar": ["نادي رياضي"]},
    "OTHER": {"*": ["Salon", "Barber", "Beauty Parlour", "Spa & Salon", "Hair Studio", "Nail Spa"],
              "IN": ["Unisex Salon", "Gents Parlour", "Hair Cutting Saloon"],
              "US": ["Barbershop", "Hair Salon", "Nail Bar"],
              "AE": ["Gents Salon", "Ladies Salon", "Barber Shop LLC"],
              "SA": ["Men's Salon", "Barber"],
              "ar": ["صالون", "حلاق"]},
}

NAME_STEMS = {
    "IN": ["Sri Balaji", "Ganesh", "Krishna", "Royal", "New", "Shree", "Annapurna", "Sai", "Laxmi", "Om",
           "Durga", "Shiva", "Murugan", "Vinayaka", "Green Leaf", "Golden", "Hari Om", "Jai Hind", "Anand",
           "Sagar", "Kamat", "Venkateshwara", "Maruti", "Mahalakshmi", "Sunrise", "Navrang", "Rajdhani"],
    "US": ["Main Street", "Oak", "Golden", "Liberty", "Sunset", "Maple", "Blue Ridge", "Lakeside", "Joe's",
           "Riverside", "Hilltop", "Union", "Pioneer", "Evergreen", "Harbor", "Cedar", "Metro", "Elm Street",
           "Bay Area", "Lone Star", "Red Rock", "Brooklyn", "Midtown", "Pacific"],
    "AE": ["Al Noor", "Al Madina", "Al Safa", "Al Rashid", "Golden Falcon", "Al Barsha", "Al Quoz", "Jumeirah",
           "Deira", "Karama", "Marina", "Al Wasl", "Al Khaleej", "Desert Rose", "Palm", "Al Reem", "Bur Dubai",
           "Al Nahda", "Emirates Star", "Silver Sand"],
    "SA": ["Al Noor", "Al Riyadh", "Al Olaya", "Al Malqa", "Al Hamra", "Al Andalus", "Najd", "Al Rawdah",
           "Al Yasmin", "Al Nakheel", "Al Salam", "Al Fayha", "Jeddah", "Hijaz", "Al Qassim", "Al Sahafa",
           "Al Wurud", "Diriyah", "Al Aziziyah", "Al Khobar"],
    "ar": ["النور", "السلام", "الرياض", "الأندلس", "النخيل", "الياسمين", "المدينة", "الخليج"],
}

# names with no category cue; their labels are spread on purpose so the model learns doubt
OPAQUE_SUFFIX = {
    "IN": ["Enterprises", "Traders", "Associates", "Agencies", "Corporation", "Ventures", "& Sons", "Pvt Ltd"],
    "US": ["LLC", "Inc", "Holdings", "Group", "& Co", "Services", "Partners"],
    "AE": ["Trading LLC", "General Trading", "FZE", "LLC", "Services LLC", "Enterprises"],
    "SA": ["Trading Est", "Co", "Est", "Group", "Company", "Services"],
}
OPAQUE_LABELS = ["SHOPPING", "OTHER", "FOOD_DINING", "SHOPPING", "GROCERIES", "TRANSFER"]

DEFAULT_KIND = {
    "FOOD_DINING": ["purchase", "purchase", "online"],
    "GROCERIES": ["purchase", "purchase", "online"],
    "SHOPPING": ["purchase", "online", "online"],
    "TRANSPORTATION": ["purchase", "purchase", "online"],
    "UTILITIES": ["bill", "bill", "online"],
    "ENTERTAINMENT": ["online", "purchase", "recurring"],
    "HEALTHCARE": ["purchase", "purchase", "online"],
    "EDUCATION": ["bill", "online", "purchase"],
    "TRAVEL": ["online", "online", "purchase"],
    "SUBSCRIPTIONS": ["recurring", "recurring", "online"],
    "FINANCE": ["bill", "bill", "online"],
    "OTHER": ["purchase"],
}

TEMPLATES = {
    "purchase": {
        "IN": ["POS {card} {M} {city}", "POS PURCHASE {M} {city}", "{M} {city} IN", "UPI/DR/{ref}/{M}/{bank4}/{vpa}/UPI",
               "UPI-{M}-{vpa}-{ifsc}-{ref}-PAYMENT FROM PHONE", "PCD/{card}/{M}/{city}", "{M} {city}",
               "DEBIT CARD PURCHASE {M}", "UPI/{ref}/{M}/Paid via App"],
        "US": ["{M} #{store} {city} {st}", "CHECKCARD {mmdd} {M} {city} {st} {ref}",
               "PURCHASE AUTHORIZED ON {md} {M} {city} {st} S{ref} CARD {last4}", "SQ *{M} {city} {st}",
               "TST* {M} {city} {st}", "{M} {city} {st}", "POS DEBIT {M} {city} {st}", "DEBIT CARD PURCHASE {M} {st}"],
        "AE": ["POS PURCHASE {M} {city} AE", "{M} {city} ARE", "APPLE PAY {M} {city} AE", "CARD PURCHASE {M} {city}",
               "DEBIT CARD TXN {M} {city} AE", "{M} {city} AE {card}", "PURCHASE {M} {city}"],
        "SA": ["MADA PURCHASE {M} {city}", "MADA {M} {city} SA", "POS {M} {city} SAU", "APPLE PAY {M} {city} SA",
               "PURCHASE {M} {city}", "شراء {M} {city}", "POS PURCHASE {M} {city} KSA"],
    },
    "online": {
        "IN": ["UPI/DR/{ref}/{M}/{bank4}/{vpa}/Pay", "ECOM PUR {M} {city}", "{M} ONLINE PAYMENT", "PAYU*{M}",
               "RAZ*{M}", "UPI-{M}-{vpa}-{ifsc}-{ref}-UPI", "{M} ORDER {ref}", "ME DC SI {card} {M}"],
        "US": ["{M}.COM {st}", "PAYPAL *{M}", "{M}*{ref6} {city} {st}", "SP {M} {st}", "{M} ONLINE {st}",
               "WWW.{MN}.COM {st}", "{M} {phone} {st}", "APPLE PAY {M}"],
        "AE": ["E-COMMERCE {M} AE", "{M} DUBAI AE", "ONLINE PURCHASE {M}", "APPLE PAY {M}", "WWW.{MN}.COM AE",
               "{M} ONLINE {city}"],
        "SA": ["ONLINE PURCHASE {M} SA", "MADA ATHEER {M}", "E-COMMERCE {M} {city}", "{M} ONLINE SA",
               "APPLE PAY {M}", "WWW.{MN}.COM SA", "شراء عبر الإنترنت {M}"],
    },
    "bill": {
        "IN": ["BIL/BPAY/{ref}/{M}", "ACH D- TP ACH {M}-{ref}", "NACH DR {M} {ref}", "{M} BILL PAYMENT",
               "BBPS/{M}/{ref}", "UPI/DR/{ref}/{M}/{bank4}/{vpa}/bill payment", "BIL/ONL/{ref}/{M}"],
        "US": ["{M} AUTOPAY", "ACH DEBIT {M} PPD ID: {ref}", "{M} WEB PMTS", "{M} BILL PAY", "ONLINE PMT {M}",
               "{M} PAYMENT {st}", "{M} ACH PMT {ref}"],
        "AE": ["{M} BILL PAYMENT", "UTILITY PAYMENT {M}", "DIRECT DEBIT {M}", "{M} ONLINE PAYMENT AE",
               "BILL PAYMENT {M} {ref}", "{M} DUBAI AE"],
        "SA": ["SADAD {M} {ref}", "SADAD PAYMENT {M}", "{M} BILL", "BILL PAYMENT {M}", "سداد {M}",
               "STANDING ORDER {M}"],
    },
    "recurring": {
        "*": ["{M} SUBSCRIPTION", "{M} MONTHLY", "{M}*RENEWAL", "{M} ANNUAL PLAN", "{M} RECURRING", "{M} MEMBERSHIP",
              "{M} AUTO RENEW"],
        "IN": ["ME DC SI {card} {M}", "SI-{M}-{ref}", "{M} ONLINE PAYMENT"],
        "US": ["{M}*{ref6} {phone} {st}", "PAYPAL *{M}", "{M}.COM/BILL {st}"],
        "AE": ["{M} AE RECURRING", "APPLE PAY {M}"],
        "SA": ["{M} SA RECURRING", "MADA ATHEER {M}"],
    },
}

EXTRA = {
    "INCOME": {
        "IN": ["SALARY CREDIT {EMP}", "NEFT CR-{ifsc}-{EMP}-SALARY {mon}", "BY SALARY {EMP}", "SAL {EMP} {mon}",
               "INT.PD", "INTEREST CREDIT", "SB INTEREST", "CASHBACK FROM {wallet}", "REFUND {brand}",
               "{brand} REFUND", "DIVIDEND {EMP}", "ACH C- {EMP} DIVIDEND", "IMPS-{ref}-{EMP}-REIMBURSEMENT",
               "UPI/CR/{ref}/{brand}/REFUND", "FD INTEREST", "STIPEND {EMP}", "BONUS {EMP}"],
        "US": ["{EMP} PAYROLL PPD ID: {ref}", "DIRECT DEP {EMP}", "{EMP} DIR DEP", "INTEREST PAYMENT",
               "INTEREST EARNED", "{brand} REFUND", "REFUND FROM {brand}", "CASHBACK REWARD", "{broker} DIVIDEND",
               "IRS TREAS 310 TAX REF", "ACH CREDIT {EMP} PAYROLL", "MOBILE DEPOSIT", "STATEMENT CREDIT REFUND {brand}"],
        "AE": ["SALARY TRANSFER {EMP}", "WPS SALARY {EMP}", "PAYROLL {EMP}", "PROFIT CREDIT", "INTEREST CREDIT",
               "REFUND {brand}", "CASHBACK {bankname}", "DIVIDEND {EMP}", "SALARY CREDIT {mon}", "END OF SERVICE {EMP}"],
        "SA": ["PAYROLL CREDIT {EMP}", "SALARY {EMP}", "راتب {EMP}", "PROFIT DISTRIBUTION", "REFUND {brand}",
               "استرداد {brand}", "CASHBACK", "DIVIDEND {EMP}", "SALARY DEPOSIT", "حوالة راتب", "HOUSING ALLOWANCE {EMP}"],
    },
    "TRANSFER": {
        "IN": ["UPI/DR/{ref}/{PERSON}/{bank4}/{pvpa}/Sent", "IMPS/P2A/{ref}/{PERSON}", "NEFT DR-{ifsc}-{PERSON}-{ref}",
               "TRANSFER TO {PERSON}", "UPI/CR/{ref}/{PERSON}/{bank4}/{pvpa}/Received", "SELF TRANSFER TO SAVINGS",
               "ADD MONEY TO {wallet} WALLET", "IMPS-{ref}-{PERSON}-{bankname}", "UPI-{PERSON}-{pvpa}-{ifsc}-{ref}-SENT",
               "FUND TRANSFER TO OWN ACCOUNT", "MOB BANKING TRANSFER {PERSON}", "RTGS DR-{ifsc}-{PERSON}"],
        "US": ["ZELLE TO {PERSON} ON {md} REF # {ref}", "ZELLE FROM {PERSON}", "VENMO PAYMENT {ref}", "VENMO CASHOUT",
               "CASH APP*{PERSON}", "ONLINE TRANSFER TO SAV ...{last4}", "TRANSFER FROM CHK {last4}",
               "WIRE TRANSFER TO {PERSON}", "REMITLY", "WESTERN UNION", "PAYPAL TRANSFER", "APPLE CASH SENT MONEY",
               "ONLINE TRANSFER FROM {PERSON}"],
        "AE": ["TRANSFER TO {PERSON}", "IPP TRANSFER {PERSON}", "AANI {PERSON}", "LOCAL TRANSFER {PERSON}",
               "INTERNATIONAL TRANSFER {PERSON}", "{exchange} REMITTANCE", "OWN ACCOUNT TRANSFER",
               "FUNDS TRANSFER {PERSON}", "{exchange}"],
        "SA": ["STC PAY TRANSFER", "SARIE TRANSFER {PERSON}", "INSTANT TRANSFER {PERSON}", "تحويل {PERSON_AR}",
               "INTERNAL TRANSFER", "ENJAZ REMITTANCE", "TAHWEEL ALRAJHI", "URPAY TRANSFER", "QUICK PAY {PERSON}",
               "تحويل داخلي", "LOCAL TRANSFER {PERSON}"],
    },
    "OTHER": {
        "IN": ["ATM WDL {city}", "ATW-{card}-{bankname} ATM {city}", "NWD-{card}-{ref}-{city}", "CASH WITHDRAWAL {city}",
               "PASSPORT SEVA", "RTO {city}", "E-CHALLAN TRAFFIC POLICE", "PROPERTY TAX {city} MUNICIPAL CORPORATION",
               "INCOME TAX PAYMENT", "GIVEINDIA DONATION", "AKSHAYA PATRA", "ISKCON DONATION", "PM CARES FUND",
               "TEMPLE DONATION", "INDIA POST", "SPEED POST {city}", "DTDC COURIER", "BLUE DART", "NATURALS SALON",
               "LAKME SALON", "GREEN TRENDS", "STAMP DUTY {city}"],
        "US": ["ATM WITHDRAWAL {city} {st}", "ATM CASH {city}", "CASH WITHDRAWAL {bankname}", "DMV {st}",
               "DEPT OF MOTOR VEHICLES {st}", "USCIS", "CITY OF {city} PARKING TICKET", "COUNTY CLERK {st}",
               "IRS USATAXPYMT", "RED CROSS", "UNICEF USA", "SALVATION ARMY", "ST JUDE", "WIKIMEDIA", "GOFUNDME",
               "CHURCH TITHE", "USPS PO {city} {st}", "THE UPS STORE", "SUPERCUTS", "SPORT CLIPS", "FANTASTIC SAMS",
               "PASSPORT SERVICES"],
        "AE": ["ATM CASH WITHDRAWAL {city}", "ATM WDL {bankname}", "CASH WITHDRAWAL {city}", "DUBAI POLICE FINES",
               "AMER CENTER {city}", "TASHEEL", "GDRFA VISA", "DUBAI COURTS", "EMIRATES ID RENEWAL", "MOI UAE",
               "EMIRATES RED CRESCENT", "DUBAI CARES", "DAR AL BER", "BEIT AL KHAIR", "ZAKAT FUND", "EMIRATES POST",
               "ARAMEX", "TIPS & TOES", "TRAFFIC FINE PAYMENT"],
        "SA": ["ATM WITHDRAWAL {city}", "سحب نقدي {city}", "CASH WITHDRAWAL MADA ATM", "ABSHER", "MOI TRAFFIC VIOLATION",
               "JAWAZAT", "MUQEEM", "SADAD MOI", "NAJIZ", "EHSAN", "KSRELIEF", "ZAKAT", "تبرع", "SAUDI POST SPL",
               "SPL", "ARAMEX", "SMSA EXPRESS", "مخالفات مرورية", "ISTIQDAM VISA FEES"],
    },
    "FINANCE": {
        "IN": ["SMS ALERT CHARGES", "ANNUAL FEE DEBIT CARD", "SERVICE CHARGES", "GST ON CHARGES", "MIN BAL CHARGES",
               "ATM AMC CHGS", "CHQ BOOK CHARGES", "CC PAYMENT {bankname}", "{bankname} CREDIT CARD PAYMENT",
               "{bankname} LOAN EMI", "LOAN EMI {bankname}", "NACH DR {bankname} PERSONAL LOAN", "SIP {bankname} MF",
               "PPF DEPOSIT", "RD INSTALLMENT", "LATE PAYMENT CHARGES", "PROCESSING FEE {bankname}"],
        "US": ["MONTHLY SERVICE FEE", "OVERDRAFT FEE", "FOREIGN TRANSACTION FEE", "WIRE TRANSFER FEE", "ATM FEE",
               "LATE FEE", "{bankname} CREDIT CRD AUTOPAY", "{bankname} EPAYMENT", "PAYMENT TO {bankname} CARD ENDING IN {last4}",
               "{bankname} AUTO LOAN PMT", "MORTGAGE PMT {bankname}", "{bankname} LOAN PAYMENT", "ANNUAL MEMBERSHIP FEE"],
        "AE": ["ACCOUNT MAINTENANCE FEE", "VAT ON FEES", "CARD ANNUAL FEE", "LATE PAYMENT FEE", "CREDIT CARD PAYMENT {bankname}",
               "CC REPAYMENT {bankname}", "LOAN INSTALLMENT {bankname}", "PERSONAL LOAN REPAYMENT", "AUTO LOAN EMI",
               "MORTGAGE INSTALLMENT {bankname}", "FALL BELOW FEE"],
        "SA": ["رسوم خدمة", "ACCOUNT FEES", "VAT ON CHARGES", "CARD ISSUANCE FEE", "CREDIT CARD PAYMENT",
               "سداد بطاقة ائتمانية", "FINANCE INSTALLMENT {bankname}", "قسط تمويل", "MURABAHA INSTALLMENT",
               "PERSONAL FINANCE {bankname}", "REAL ESTATE FINANCE {bankname}"],
    },
}
CREDIT_CATEGORIES = {"INCOME"}
WALLETS = ["PAYTM", "PHONEPE", "AMAZON PAY", "MOBIKWIK", "FREECHARGE", "GPAY"]
BROKERS = ["VANGUARD", "FIDELITY", "SCHWAB", "ROBINHOOD"]
EXCHANGES = ["AL ANSARI EXCHANGE", "LULU EXCHANGE", "AL FARDAN EXCHANGE", "WALL STREET EXCHANGE", "WISE", "REMITLY"]
MONTHS = ["JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"]

BRAND_SAMPLES = 8
GENERIC_PER_REGION = 24
GENERIC_SAMPLES = 3
EXTRA_SAMPLES = {"INCOME": 16, "TRANSFER": 16, "OTHER": 8, "FINANCE": 8}
OPAQUE_PER_REGION = 30


def build(seed: int = 7) -> list[dict]:
    """The full corpus; identical for the same seed."""
    rng = random.Random(seed)
    rows: list[dict] = []
    for category, regions in BRANDS.items():
        for region in REGIONS:
            # brands sold in every market get fewer rows per market so the class is not swamped
            names = [(n, BRAND_SAMPLES) for n in regions.get(region, [])]
            names += [(n, BRAND_SAMPLES // 2) for n in regions.get("*", [])]
            for name, samples in names:
                for _ in range(samples):
                    rows.append(_row(rng, category, region, name, f"brand:{category}:{name.lower()}"))
    for category, nouns in GENERIC.items():
        for region in REGIONS:
            for _ in range(GENERIC_PER_REGION):
                name, arabic = _generic_name(rng, region, nouns)
                for _ in range(GENERIC_SAMPLES):
                    rows.append(_row(rng, category, region, name, f"generic:{category}:{name.lower()}", arabic))
    for category, regions in EXTRA.items():
        for region, templates in regions.items():
            for i, template in enumerate(templates):
                for _ in range(EXTRA_SAMPLES[category]):
                    rows.append(_extra_row(rng, category, region, template, f"extra:{category}:{region}:{i}"))
    for region in REGIONS:
        for _ in range(OPAQUE_PER_REGION):
            name = f"{rng.choice(NAME_STEMS[region])} {rng.choice(OPAQUE_SUFFIX[region])}"
            category = rng.choice(OPAQUE_LABELS)
            rows.append(_row(rng, category, region, name, f"opaque:{name.lower()}", kind="purchase"))
    return rows


def _row(rng, category, region, name, group, arabic=False, kind=None):
    kind = kind or rng.choice(DEFAULT_KIND.get(category, ["purchase"]))
    templates = TEMPLATES[kind].get(region, []) + TEMPLATES[kind].get("*", [])
    descriptor = name if arabic else _descriptor(rng, name)
    description = _fill(rng, rng.choice(templates), region, M=descriptor)
    return _finish(rng, category, region, description, name, descriptor, group, credit=False)


def _extra_row(rng, category, region, template, group):
    values = {
        "EMP": rng.choice(EMPLOYERS[region]).upper(),
        "PERSON": f"{rng.choice(FIRST_NAMES[region])} {rng.choice(LAST_NAMES[region])}".upper(),
        "PERSON_AR": rng.choice(PEOPLE_AR),
        "brand": _descriptor(rng, _any_brand(rng, region)),
    }
    description = _fill(rng, template, region, **values)
    credit = category in CREDIT_CATEGORIES or (category == "TRANSFER" and rng.random() < 0.4)
    merchant_name = _extra_merchant(category, template, values)
    return _finish(rng, category, region, description, merchant_name, merchant_name, group, credit)


def _extra_merchant(category, template, values):
    if "{PERSON}" in template:
        return values["PERSON"].title()
    if "{EMP}" in template:
        return values["EMP"].title()
    if "{brand}" in template:
        return values["brand"].title()
    return None


def _finish(rng, category, region, description, name, descriptor, group, credit):
    roll = rng.random()
    if name is None:
        # the resolver falls back to the cleaned descriptor when nothing names the merchant
        merchant = description.title() if roll < 0.5 else ""
    elif roll < 0.10:
        merchant = ""
    elif roll < 0.65:
        merchant = name
    else:
        merchant = descriptor.title() if descriptor.isascii() else descriptor
    direction = None
    if rng.random() < 0.5:
        direction = "CREDIT" if credit else "DEBIT"
    if rng.random() < 0.3:
        description = description.title()
    return {"description": description, "merchant": merchant, "direction": direction,
            "category": category, "region": region, "group": group}


def _descriptor(rng, name):
    text = name.upper().replace("'", "")
    roll = rng.random()
    if roll < 0.12:
        text = text.replace(" ", "")
    elif roll < 0.22 and len(text) > 12:
        text = text[:rng.randint(10, max(10, len(text) - 1))].rstrip()
    elif roll < 0.30:
        text = text.replace("&", "AND").replace(".", " ")
    return text


def _generic_name(rng, region, nouns):
    if region in ("AE", "SA") and nouns.get("ar") and rng.random() < 0.2:
        return f"{rng.choice(nouns['ar'])} {rng.choice(NAME_STEMS['ar'])}", True
    noun = rng.choice(nouns.get(region, []) + nouns["*"])
    return f"{rng.choice(NAME_STEMS[region])} {noun}", False


def _any_brand(rng, region):
    category = rng.choice(["FOOD_DINING", "SHOPPING", "GROCERIES", "TRAVEL"])
    return rng.choice(BRANDS[category][region])


def _fill(rng, template, region, **values):
    city = rng.choice(CITIES[region])
    m = values.get("M", "")
    fields = {
        "city": city,
        "st": US_STATES.get(city, rng.choice(list(US_STATES.values()))),
        "ref": _digits(rng, rng.choice([8, 10, 12])),
        "ref6": "".join(rng.choice(string.ascii_uppercase + string.digits) for _ in range(6)),
        "card": f"{_digits(rng, 6)}XXXXXX{_digits(rng, 4)}" if rng.random() < 0.5 else f"XXXX{_digits(rng, 4)}",
        "last4": _digits(rng, 4),
        "store": _digits(rng, rng.choice([3, 4, 5])),
        "mmdd": f"{rng.randint(1, 12):02d}{rng.randint(1, 28):02d}",
        "md": f"{rng.randint(1, 12):02d}/{rng.randint(1, 28):02d}",
        "phone": f"{_digits(rng, 3)}-{_digits(rng, 3)}-{_digits(rng, 4)}",
        "bank4": rng.choice(IFSC_PREFIX),
        "ifsc": f"{rng.choice(IFSC_PREFIX)}0{_digits(rng, 6)}",
        "vpa": f"{m.lower().replace(' ', '')[:12] or 'merchant'}@{rng.choice(UPI_HANDLES)}",
        "pvpa": f"{_digits(rng, 10)}@{rng.choice(UPI_HANDLES)}",
        "bankname": rng.choice(BANKS[region]),
        "wallet": rng.choice(WALLETS),
        "broker": rng.choice(BROKERS),
        "exchange": rng.choice(EXCHANGES),
        "mon": f"{rng.choice(MONTHS)} {rng.choice([2024, 2025, 2026])}",
        "MN": m.replace(" ", ""),
    }
    fields.update(values)
    return template.format(**fields)


def _digits(rng, n):
    return "".join(rng.choice(string.digits) for _ in range(n))
