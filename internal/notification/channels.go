package notification

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"time"
)

type WhatsApp struct {
	apiURL         string
	phoneNumberID  string
	accessToken    string
	httpClient     *http.Client
}

func NewWhatsApp(apiURL, phoneNumberID, accessToken string) *WhatsApp {
	return &WhatsApp{
		apiURL:        apiURL,
		phoneNumberID: phoneNumberID,
		accessToken:   accessToken,
		httpClient:    &http.Client{Timeout: 10 * time.Second},
	}
}

func (w *WhatsApp) SendOTP(ctx context.Context, phone, otp string) error {
	return w.sendTemplate(ctx, phone, "otp_delivery", []map[string]string{
		{"type": "text", "text": otp},
	})
}

func (w *WhatsApp) SendLowStockAlert(ctx context.Context, phone, partName string, qty int) error {
	return w.sendTemplate(ctx, phone, "low_stock_alert", []map[string]string{
		{"type": "text", "text": partName},
		{"type": "text", "text": fmt.Sprintf("%d", qty)},
	})
}

func (w *WhatsApp) sendTemplate(ctx context.Context, phone, templateName string, params []map[string]string) error {
	if w.phoneNumberID == "" || w.accessToken == "" {
		slog.Warn("whatsapp credentials missing, skipping send", "template", templateName)
		return nil
	}
	body := map[string]any{
		"messaging_product": "whatsapp",
		"to":                "91" + phone,
		"type":              "template",
		"template": map[string]any{
			"name":     templateName,
			"language": map[string]string{"code": "en_IN"},
			"components": []map[string]any{
				{"type": "body", "parameters": params},
			},
		},
	}
	raw, err := json.Marshal(body)
	if err != nil {
		return err
	}
	url := fmt.Sprintf("%s/%s/messages", w.apiURL, w.phoneNumberID)
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(raw))
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+w.accessToken)
	req.Header.Set("Content-Type", "application/json")
	resp, err := w.httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 {
		slog.Error("whatsapp send failed", "status", resp.StatusCode, "template", templateName)
	}
	return nil
}

type FCM struct {
	credentialsFile string
}

func NewFCM(credentialsFile string) *FCM {
	return &FCM{credentialsFile: credentialsFile}
}

func (f *FCM) SendLowStockPush(ctx context.Context, userID, partName string, qty int) error {
	if f.credentialsFile == "" {
		slog.Warn("firebase credentials missing, skipping push", "userId", userID)
		return nil
	}
	slog.Info("fcm low-stock push queued", "userId", userID, "part", partName, "qty", qty)
	return nil
}
