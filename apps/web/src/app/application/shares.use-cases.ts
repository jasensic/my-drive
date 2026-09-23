import { Inject, Injectable } from '@angular/core';
import { ShareGrant, SharePermission, ShareResourceType } from '../domain/models';
import { SHARE_REPOSITORY, ShareRepository } from '../domain/ports';
import { CreateShare, ListShares, RevokeShare } from './use-cases.tokens';

@Injectable()
export class ListSharesService implements ListShares {
  constructor(@Inject(SHARE_REPOSITORY) private readonly shares: ShareRepository) {}
  execute(resourceType?: ShareResourceType, resourceId?: string): Promise<ShareGrant[]> {
    return this.shares.list(resourceType, resourceId);
  }
}

@Injectable()
export class CreateShareService implements CreateShare {
  constructor(@Inject(SHARE_REPOSITORY) private readonly shares: ShareRepository) {}
  execute(input: {
    resourceType: ShareResourceType;
    resourceId: string;
    granteeId: string;
    permission: SharePermission;
  }): Promise<ShareGrant> {
    if (!input.granteeId) {
      return Promise.reject(new Error('Choose a user to share with'));
    }
    return this.shares.create(input);
  }
}

@Injectable()
export class RevokeShareService implements RevokeShare {
  constructor(@Inject(SHARE_REPOSITORY) private readonly shares: ShareRepository) {}
  execute(id: string): Promise<void> {
    return this.shares.remove(id);
  }
}
