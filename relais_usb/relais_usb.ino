const int RELAY_PIN = D1; // Broche du shield relais Wemos

void setup() {
  Serial.begin(115200);       // Ouvre la communication USB
  pinMode(RELAY_PIN, OUTPUT);
  digitalWrite(RELAY_PIN, LOW); // Éteint par défaut
}

void loop() {
  if (Serial.available() > 0) {
    char commande = Serial.read(); // Lit le caractère envoyé par le Pi
    
    if (commande == '1') {
      digitalWrite(RELAY_PIN, HIGH); // Allume le relais
      Serial.println("OK:ON");       
    } 
    else if (commande == '0') {
      digitalWrite(RELAY_PIN, LOW);  // Éteint le relais
      Serial.println("OK:OFF");      
    }
  }
}
