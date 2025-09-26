export interface ExternalEnvironment {
  id?: number;
  name: string;
  description?: string;
}

export interface ExternalLocation {
  id: number;
  label: string;
  dbConnectionName: string;
  site?: string;
  details?: string;
}

export interface SenderCandidate {
  idSender: number | null;
  name: string;
}

export interface EnqueueRequest {
  senderId?: number;
  payloadIds: string[];
  source?: string;
}
