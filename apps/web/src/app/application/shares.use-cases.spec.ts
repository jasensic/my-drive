import { CreateUserService, ListUsersService, LoginService } from './auth.use-cases';
import { CreateShareService, ListSharesService, RevokeShareService } from './shares.use-cases';
import { AuthRepository, TokenStore } from '../domain/ports';
import { UserProfile } from '../domain/models';

function authRepository(overrides: Partial<AuthRepository> = {}): AuthRepository {
  return {
    status: async () => ({ setup_required: false }),
    setup: async () => Promise.reject(new Error('unused')),
    login: async () => Promise.reject(new Error('unused')),
    me: async () => ({ id: 'u1', username: 'admin', is_admin: true }),
    listUsers: async () => [],
    createUser: async () => Promise.reject(new Error('unused')),
    ...overrides,
  };
}

describe('CreateUserService', () => {
  it('creates the account without signing the admin out', async () => {
    const createUser = vi.fn(async (username: string) => ({ id: 'u2', username, is_admin: false }));
    const user = await new CreateUserService(authRepository({ createUser })).execute(' friend ', 'password123');
    expect(user).toEqual({ id: 'u2', username: 'friend', is_admin: false });
    expect(createUser).toHaveBeenCalledWith('friend', 'password123');
  });

  it('rejects short passwords before calling the server', async () => {
    const createUser = vi.fn();
    await expect(new CreateUserService(authRepository({ createUser })).execute('friend', 'short')).rejects.toThrow(
      'at least 8',
    );
    expect(createUser).not.toHaveBeenCalled();
  });
});

describe('LoginService', () => {
  it('remembers whether the user is an admin', async () => {
    const tokens: TokenStore = {
      get: () => null,
      username: () => null,
      userId: () => null,
      isAdmin: () => false,
      set: vi.fn(),
      clear: () => undefined,
    };
    const auth = authRepository({
      login: async (username) => ({ token: 't', user: { id: 'u1', username, is_admin: true } }),
    });
    await new LoginService(auth, tokens).execute('admin', 'password123');
    expect(tokens.set).toHaveBeenCalledWith('t', 'admin', 'u1', true);
  });
});

describe('ListUsersService', () => {
  it('returns accounts without using password hashes', async () => {
    const users: UserProfile[] = [
      { id: '1', username: 'admin' },
      { id: '2', username: 'friend' },
    ];
    const auth = authRepository({ me: async () => users[0], listUsers: async () => users });
    await expect(new ListUsersService(auth).execute()).resolves.toEqual(users);
  });
});

describe('CreateShareService', () => {
  it('rejects a missing grantee', async () => {
    const shares = {
      list: async () => [],
      create: async () => Promise.reject(new Error('should not create')),
      remove: async () => undefined,
    };
    await expect(
      new CreateShareService(shares).execute({
        resourceType: 'file',
        resourceId: 'f1',
        granteeId: '',
        permission: 'read',
      }),
    ).rejects.toThrow(/Choose a user/);
  });

  it('creates a share through the repository', async () => {
    const created = {
      id: 's1',
      resource_type: 'album' as const,
      resource_id: 'a1',
      owner_id: 'u1',
      grantee_id: 'u2',
      grantee_username: 'friend',
      permission: 'write' as const,
      created_at: '',
    };
    const shares = {
      list: async () => [],
      create: async () => created,
      remove: async () => undefined,
    };
    await expect(
      new CreateShareService(shares).execute({
        resourceType: 'album',
        resourceId: 'a1',
        granteeId: 'u2',
        permission: 'write',
      }),
    ).resolves.toEqual(created);
  });
});

describe('ListSharesService and RevokeShareService', () => {
  it('lists and revokes through the repository', async () => {
    const listed = [
      {
        id: 's1',
        resource_type: 'file' as const,
        resource_id: 'f1',
        owner_id: 'u1',
        grantee_id: 'u2',
        grantee_username: 'friend',
        permission: 'read' as const,
        created_at: '',
      },
    ];
    const shares = {
      list: async () => listed,
      create: async () => listed[0],
      remove: vi.fn().mockResolvedValue(undefined),
    };
    await expect(new ListSharesService(shares).execute('file', 'f1')).resolves.toEqual(listed);
    await new RevokeShareService(shares).execute('s1');
    expect(shares.remove).toHaveBeenCalledWith('s1');
  });
});
