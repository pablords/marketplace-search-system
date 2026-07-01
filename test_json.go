package main
import (
	"encoding/json"
	"fmt"
)
type SellerReputation struct {
	TotalReviews    *int `json:"total_reviews,omitempty"`
	PositiveReviews *int `json:"positive_reviews,omitempty"`
	NeutralReviews  *int `json:"neutral_reviews,omitempty"`
	NegativeReviews *int `json:"negative_reviews,omitempty"`
}
func main() {
	j := `{"total_reviews":1900,"positive_reviews":1463,"neutral_reviews":437,"negative_reviews":0}`
	var rep SellerReputation
	json.Unmarshal([]byte(j), &rep)
	b, _ := json.Marshal(rep)
	fmt.Println(string(b))
}
