import secrets

from fastapi import HTTPException, Request, status


def require_service_token(request: Request) -> None:
    expected: str = request.app.state.settings.service_token
    header = request.headers.get("authorization", "")
    scheme, _, presented = header.partition(" ")
    # An unset token fails closed: nothing but /health is reachable.
    if not expected or scheme.lower() != "bearer" or not secrets.compare_digest(
        presented.encode(), expected.encode()
    ):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="unauthorized",
            headers={"WWW-Authenticate": "Bearer"},
        )
