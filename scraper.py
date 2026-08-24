import requests
from bs4 import BeautifulSoup
import json
import re

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Referer": "https://www.mdlottery.com/"
}

def calculate_roi(prizes, ticket_price, probability):
    """
    Calculates Expected Value (EV) and Return on Investment (ROI).
    EV = Total Remaining Prize Money / Total Estimated Remaining Tickets
    ROI = (EV / Ticket Price) * 100
    """
    total_remaining_prizes = sum(tier['remaining'] for tier in prizes)
    total_remaining_value = sum(tier['amount'] * tier['remaining'] for tier in prizes)
    
    if total_remaining_prizes == 0 or probability <= 0:
        return 0.0, 0.0
        
    estimated_tickets_remaining = total_remaining_prizes * probability
    expected_value = total_remaining_value / estimated_tickets_remaining
    roi_percentage = (expected_value / ticket_price) * 100
    
    return round(expected_value, 4), round(roi_percentage, 2)

def scrape_md_lottery():
    base_url = "https://www.mdlottery.com/games/scratch-offs/"
    response = requests.get(base_url, headers=HEADERS)
    soup = BeautifulSoup(response.text, 'html.parser')
    
    games_data = []
    
    # Extract links to individual game detail pages
    game_links = soup.select("a[href*='/games/scratch-offs/']")
    urls = list(set([link['href'] for link in game_links if link['href'] != base_url]))

    for url in urls:
        try:
            res = requests.get(url, headers=HEADERS, timeout=10)
            game_soup = BeautifulSoup(res.text, 'html.parser')
            
            title = game_soup.select_one("h1").text.strip()
            price_text = game_soup.select_one(".game-price, .price").text
            price = int(re.sub(r'[^0-9]', '', price_text))
            
            odds_text = game_soup.find(text=re.compile(r'1 in \d+\.\d+'))
            probability = float(re.search(r'\d+\.\d+', odds_text).group()) if odds_text else 4.0
            
            # Scrape Prize Table
            prizes = []
            table = game_soup.find("table")
            for row in table.select("tbody tr"):
                cols = row.select("td")
                if len(cols) >= 3:
                    amount = int(re.sub(r'[^0-9]', '', cols[0].text))
                    remaining = int(re.sub(r'[^0-9]', '', cols[2].text))
                    prizes.append({"amount": amount, "remaining": remaining})
            
            ev, roi = calculate_roi(prizes, price, probability)
            
            games_data.append({
                "title": title,
                "price": price,
                "probability": probability,
                "expectedValue": ev,
                "roiPercentage": roi,
                "prizes": prizes
            })
        except Exception as e:
            continue

    with open("scratch_offs_roi.json", "w") as f:
        json.dump(games_data, f, indent=2)

if __name__ == "__main__":
    scrape_md_lottery()
