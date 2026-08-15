package upload

import (
	"context"
	"fmt"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	awsconfig "github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/credentials"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	"github.com/google/uuid"

	"github.com/autoparts/inventory-management/internal/apperr"
)

type Service struct {
	presign       *s3.PresignClient
	client        *s3.Client
	bucket        string
	cloudfrontURL string
}

func New(ctx context.Context, region, accessKey, secretKey, bucket, cloudfrontURL string) (*Service, error) {
	cfg, err := awsconfig.LoadDefaultConfig(ctx,
		awsconfig.WithRegion(region),
		awsconfig.WithCredentialsProvider(credentials.NewStaticCredentialsProvider(accessKey, secretKey, "")),
	)
	if err != nil {
		return nil, err
	}
	client := s3.NewFromConfig(cfg)
	return &Service{
		presign:       s3.NewPresignClient(client),
		client:        client,
		bucket:        bucket,
		cloudfrontURL: cloudfrontURL,
	}, nil
}

func (s *Service) Presign(ctx context.Context, userID uuid.UUID, filename, contentType string) (map[string]string, error) {
	if contentType != "image/jpeg" && contentType != "image/png" {
		return nil, apperr.BadRequest("INVALID_FILE_TYPE", "Only JPEG and PNG images are supported")
	}
	ext := ".jpg"
	if contentType == "image/png" {
		ext = ".png"
	}
	key := fmt.Sprintf("parts/%s/%s%s", userID, uuid.NewString(), ext)
	out, err := s.presign.PresignPutObject(ctx, &s3.PutObjectInput{
		Bucket:      aws.String(s.bucket),
		Key:         aws.String(key),
		ContentType: aws.String(contentType),
	}, func(opts *s3.PresignOptions) {
		opts.Expires = 15 * time.Minute
	})
	if err != nil {
		return nil, err
	}
	return map[string]string{
		"upload_url": out.URL,
		"public_url": s.cloudfrontURL + "/" + key,
		"key":        key,
		"filename":   filename,
	}, nil
}
