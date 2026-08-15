package model

type BusinessType string

const (
	BusinessShop          BusinessType = "SHOP"
	BusinessServiceCenter BusinessType = "SERVICE_CENTER"
	BusinessBoth          BusinessType = "BOTH"
)

type OnboardingStatus string

const (
	OnboardingRegistered OnboardingStatus = "REGISTERED"
	OnboardingProfiled   OnboardingStatus = "PROFILED"
	OnboardingActive     OnboardingStatus = "ACTIVE"
)

type VehicleCategory string

const (
	VehicleTwoWheeler   VehicleCategory = "TWO_WHEELER"
	VehicleFourWheeler  VehicleCategory = "FOUR_WHEELER"
	VehicleThreeWheeler VehicleCategory = "THREE_WHEELER"
	VehicleCommercial   VehicleCategory = "COMMERCIAL"
	VehicleEV           VehicleCategory = "EV"
)

func (v VehicleCategory) Valid() bool {
	switch v {
	case VehicleTwoWheeler, VehicleFourWheeler, VehicleThreeWheeler, VehicleCommercial, VehicleEV:
		return true
	default:
		return false
	}
}

type ChangeType string

const (
	ChangeAdd        ChangeType = "ADD"
	ChangeSold       ChangeType = "SOLD"
	ChangeReceived   ChangeType = "RECEIVED"
	ChangeAdjustment ChangeType = "ADJUSTMENT"
	ChangeReturned   ChangeType = "RETURNED"
)

type NotificationType string

const (
	NotifyLowStock NotificationType = "LOW_STOCK"
	NotifyOTPSent  NotificationType = "OTP_SENT"
)

type NotificationChannel string

const (
	ChannelWhatsApp NotificationChannel = "WHATSAPP"
	ChannelPush     NotificationChannel = "PUSH"
	ChannelInApp    NotificationChannel = "IN_APP"
)
