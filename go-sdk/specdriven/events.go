package specdriven

import (
	"context"
	"errors"
	"time"
)

// Event represents one backend event returned by the Java HTTP API.
type Event struct {
	Sequence  int64          `json:"sequence"`
	Type      string         `json:"type"`
	Timestamp int64          `json:"timestamp"`
	Source    string         `json:"source"`
	Metadata  map[string]any `json:"metadata"`
}

// EventPollResponse represents GET /api/v1/events.
type EventPollResponse struct {
	Events     []Event `json:"events"`
	NextCursor int64   `json:"nextCursor"`
}

// EventPollOptions customizes one event polling request.
type EventPollOptions struct {
	After int64
	Limit int
	Type  string
}

// EventSubscribeOptions customizes the polling subscription loop.
type EventSubscribeOptions struct {
	After        int64
	Limit        int
	Type         string
	PollInterval time.Duration
}

// EventHandler receives one event from a polling subscription.
type EventHandler func(context.Context, Event) error

// Events provides high-level event operations backed by a Client.
type Events struct {
	client *Client
}

// NewEvents creates an Events handle backed by an existing Client.
func NewEvents(client *Client) (*Events, error) {
	if client == nil {
		return nil, errors.New("specdriven: client must not be nil")
	}
	return &Events{client: client}, nil
}

// Poll returns currently retained backend events matching the options.
func (e *Events) Poll(ctx context.Context, options EventPollOptions) (*EventPollResponse, error) {
	return e.client.PollEvents(ctx, options)
}

// Subscribe polls for backend events until the context is canceled or an error occurs.
func (e *Events) Subscribe(ctx context.Context, options EventSubscribeOptions, handler EventHandler) error {
	if handler == nil {
		return errors.New("specdriven: event handler must not be nil")
	}
	if ctx == nil {
		ctx = context.Background()
	}
	cursor := options.After
	interval := options.PollInterval
	if interval <= 0 {
		interval = time.Second
	}

	for {
		if err := ctx.Err(); err != nil {
			return err
		}
		response, err := e.Poll(ctx, EventPollOptions{
			After: cursor,
			Limit: options.Limit,
			Type:  options.Type,
		})
		if err != nil {
			if ctxErr := ctx.Err(); ctxErr != nil {
				return ctxErr
			}
			return err
		}
		for _, event := range response.Events {
			if event.Sequence <= cursor {
				continue
			}
			if err := handler(ctx, event); err != nil {
				return err
			}
			cursor = event.Sequence
		}
		if response.NextCursor > cursor {
			cursor = response.NextCursor
		}
		if err := waitForRetry(ctx, interval); err != nil {
			return err
		}
	}
}
